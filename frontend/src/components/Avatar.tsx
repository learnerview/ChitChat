interface AvatarProps {
  name: string;
  seed: string;
  size?: number;
}

/** Initials avatar with a deterministic color derived from the seed (userId). */
export default function Avatar({ name, seed, size = 36 }: AvatarProps) {
  const hue = hash(seed) % 360;
  const initials = name
    .split(/\s+/)
    .map(part => part[0])
    .filter(Boolean)
    .slice(0, 2)
    .join('')
    .toUpperCase();

  return (
    <span
      className="avatar"
      style={{
        width: size,
        height: size,
        fontSize: size * 0.4,
        background: `linear-gradient(135deg, hsl(${hue}, 65%, 52%), hsl(${(hue + 40) % 360}, 65%, 42%))`,
      }}
      aria-hidden="true"
    >
      {initials || '?'}
    </span>
  );
}

function hash(value: string): number {
  let result = 0;
  for (let i = 0; i < value.length; i++) {
    result = (result * 31 + value.charCodeAt(i)) | 0;
  }
  return Math.abs(result);
}
