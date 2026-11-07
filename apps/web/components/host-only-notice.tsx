"use client";

export function HostOnlyNotice({ message }: { message: string }) {
  return <div className="empty-state">{message}</div>;
}
