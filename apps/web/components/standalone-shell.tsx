import Link from "next/link";
import { Logo } from "@/components/brand/logo";
import { ThemeToggle } from "@/components/theme/theme-toggle";

/** Narrow page frame for screens outside a meeting (invite, start, new meeting). */
export function StandaloneShell({ children, width = "max-w-[920px]" }: { children: React.ReactNode; width?: string }) {
  return (
    <div className="min-h-screen bg-bg text-ink">
      <header className="border-b border-line bg-surface">
        <div className={`mx-auto flex ${width} flex-wrap items-center gap-3 px-5 py-2.5`}>
          <Link href="/" aria-label="dudecide 첫 화면" className="flex min-h-[44px] items-center">
            <Logo size={26} />
          </Link>
          <div className="ml-auto">
            <ThemeToggle />
          </div>
        </div>
      </header>
      <main className={`mx-auto flex ${width} flex-col gap-4 px-5 pb-14 pt-7`}>{children}</main>
    </div>
  );
}
