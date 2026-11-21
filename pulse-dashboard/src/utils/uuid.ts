// crypto.randomUUID() only exists in secure contexts (HTTPS or localhost), so on a plain-HTTP host
// such as the Kubernetes ingress's http://pulse.local it is undefined. crypto.getRandomValues() is
// available everywhere, so it builds the same random (version 4) UUID there.

type RandomSource = Pick<Crypto, 'getRandomValues'> & Partial<Pick<Crypto, 'randomUUID'>>;

export function randomUUID(source: RandomSource = crypto): string {
  if (typeof source.randomUUID === 'function') {
    return source.randomUUID();
  }
  const bytes = source.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40; // version 4
  bytes[8] = (bytes[8] & 0x3f) | 0x80; // RFC 4122 variant
  const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
