"use client";

export function HostOnlyNotice({ message }: { message: string }) {
  return (
    <div className="rounded-[1.5rem] border border-dashed border-[color:var(--color-line)] bg-white/60 px-5 py-6 text-sm leading-6 text-ink/60">
      {message}
    </div>
  );
}
