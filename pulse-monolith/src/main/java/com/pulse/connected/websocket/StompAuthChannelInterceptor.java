package com.pulse.connected.websocket;

import com.pulse.connected.auth.JwtService;
import com.pulse.connected.auth.UserPrincipal;
import com.pulse.connected.auth.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Authenticates and authorises STOMP traffic. Browsers cannot send an Authorization header on
 * the WebSocket handshake, so the handshake is public and authentication happens here instead:
 * <ul>
 *   <li>CONNECT must carry {@code Authorization: Bearer <access token>} as a STOMP header;</li>
 *   <li>SUBSCRIBE is checked against {@link StompTopicAuthorizer};</li>
 *   <li>SEND is rejected: clients only receive server pushes.</li>
 * </ul>
 * Throwing here makes Spring send a STOMP ERROR frame and close the session.
 */
@Component
class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompAuthChannelInterceptor.class);
    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final StompTopicAuthorizer topicAuthorizer;

    StompAuthChannelInterceptor(JwtService jwtService, UserRepository userRepository,
                                StompTopicAuthorizer topicAuthorizer) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.topicAuthorizer = topicAuthorizer;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
        } else if (command == StompCommand.SUBSCRIBE) {
            UserPrincipal principal = principalOf(accessor);
            if (!topicAuthorizer.canSubscribe(principal, accessor.getDestination())) {
                log.warn("Denied STOMP subscription to [{}] for user [{}]", accessor.getDestination(),
                        principal != null ? principal.getId() : "anonymous");
                throw new AccessDeniedException("Not allowed to subscribe to " + accessor.getDestination());
            }
        } else if (command == StompCommand.SEND) {
            throw new AccessDeniedException("Clients cannot send messages");
        }
        return message;
    }

    private UsernamePasswordAuthenticationToken authenticate(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER)) {
            throw new BadCredentialsException("Missing bearer token on STOMP CONNECT");
        }
        return jwtService.parseAccessToken(authorizationHeader.substring(BEARER.length()))
                .flatMap(userRepository::findById)
                .map(UserPrincipal::new)
                .map(principal -> new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()))
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired access token on STOMP CONNECT"));
    }

    private static UserPrincipal principalOf(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken authentication
                && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        return null;
    }
}
