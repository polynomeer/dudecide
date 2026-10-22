import Link from "next/link";
import type { Route } from "next";
import { SessionBadge } from "@/components/session-badge";

const navItems = [
  { href: "/start", label: "Start Session" },
  { href: "/meetings/new", label: "Create Meeting" },
] satisfies Array<{ href: Route; label: string }>;

export function SiteHeader() {
  return (
    <header className="panel mb-4 px-5 py-4 sm:px-6">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <Link href="/" className="inline-flex items-center gap-3">
          <span className="flex h-12 w-12 items-center justify-center rounded-[1.4rem] bg-ink text-lg font-semibold text-white shadow-[0_18px_40px_rgba(13,21,38,0.18)]">
            DC
          </span>
          <div className="min-w-0">
            <div className="text-sm font-semibold uppercase tracking-[0.18em] text-muted">
              Dudecide
            </div>
            <div className="font-display text-2xl">
              Evidence-first group planning.
            </div>
          </div>
        </Link>

        <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
          <nav className="flex flex-wrap gap-2">
          {navItems.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className="action-secondary"
            >
              {item.label}
            </Link>
          ))}
          </nav>
          <SessionBadge />
        </div>
      </div>
    </header>
  );
}
