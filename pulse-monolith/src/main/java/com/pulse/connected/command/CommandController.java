package com.pulse.connected.command;

import com.pulse.connected.command.dto.CommandResponse;
import com.pulse.connected.command.dto.IssueCommandRequest;
import com.pulse.connected.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class CommandController {

    private final CommandService commandService;

    public CommandController(CommandService commandService) {
        this.commandService = commandService;
    }

    @PostMapping("/vehicles/{id}/commands")
    @PreAuthorize("@vehicleSecurityService.hasAccess(#id, principal)")
    public ResponseEntity<CommandResponse> issueCommand(
            @PathVariable UUID id,
            @Valid @RequestBody IssueCommandRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        CommandResponse response = commandService.issueCommand(id, request, principal.getUser());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/vehicles/{id}/commands/{commandId}")
    @PreAuthorize("@vehicleSecurityService.hasAccess(#id, principal)")
    public ResponseEntity<CommandResponse> getCommandStatus(
            @PathVariable UUID id,
            @PathVariable UUID commandId) {
        CommandResponse response = commandService.getCommandById(commandId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/vehicles/{id}/commands")
    @PreAuthorize("@vehicleSecurityService.hasAccess(#id, principal)")
    public ResponseEntity<List<CommandResponse>> getCommandHistory(@PathVariable UUID id) {
        List<CommandResponse> history = commandService.getCommandHistory(id);
        return ResponseEntity.ok(history);
    }

    @DeleteMapping("/commands/{commandId}")
    public ResponseEntity<CommandResponse> cancelCommand(
            @PathVariable UUID commandId,
            @AuthenticationPrincipal UserPrincipal principal) {
        CommandResponse response = commandService.cancelCommand(commandId);
        return ResponseEntity.ok(response);
    }
}
