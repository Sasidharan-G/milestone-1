/**
 * Recognises a PNG or a JPEG by its first bytes. A shop logo upload says "image/png" in a header
 * anyone can fake; this checks the file really is a picture of that kind before it is stored.
 */
export type LogoImageType = 'image/png' | 'image/jpeg';

const PNG_SIGNATURE = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

export const detectImageType = (content: Buffer): LogoImageType | null => {
  if (content.length >= PNG_SIGNATURE.length && content.subarray(0, PNG_SIGNATURE.length).equals(PNG_SIGNATURE)) return 'image/png';
  if (content.length >= 3 && content[0] === 0xff && content[1] === 0xd8 && content[2] === 0xff) return 'image/jpeg';
  return null;
};
