import Link from "next/link";
import type { Route } from "next";
import { SiteHeader } from "@/components/site-header";

type Breadcrumb = {
  label: string;
  href?: string;
};

export function SectionShell({
  title,
  eyebrow,
  description,
  breadcrumbs,
  children,
}: {
  title: string;
  eyebrow: string;
  description: string;
  breadcrumbs?: Breadcrumb[];
  children: React.ReactNode;
}) {
  return (
    <main className="shell">
      <SiteHeader />
      <section className="panel mb-5 overflow-hidden px-6 py-6 sm:px-8">
        <div className="space-y-5">
          {breadcrumbs?.length ? (
            <nav aria-label="Breadcrumb" className="flex flex-wrap items-center gap-2 text-sm text-[color:var(--color-steel)]">
              {breadcrumbs.map((item, index) => (
                <span key={`${item.label}-${index}`} className="inline-flex items-center gap-2">
                  {item.href ? (
                    <Link href={item.href as Route} className="transition hover:text-[color:var(--color-ink)]">
                      {item.label}
                    </Link>
                  ) : (
                    <span className="font-medium text-[color:var(--color-ink)]">{item.label}</span>
                  )}
                  {index < breadcrumbs.length - 1 ? <span aria-hidden="true">/</span> : null}
                </span>
              ))}
            </nav>
          ) : null}
          <div className="grid gap-5 lg:grid-cols-[0.9fr_1.1fr] lg:items-end">
            <div>
              <div className="eyebrow">{eyebrow}</div>
              <h1 className="mt-3 max-w-2xl font-[var(--font-display)] text-[2.7rem] font-semibold leading-[0.94] tracking-[-0.055em] sm:text-[3.4rem]">
                {title}
              </h1>
            </div>
            <p className="max-w-2xl text-base leading-7 text-[color:rgba(13,21,38,0.74)]">
              {description}
            </p>
          </div>
        </div>
      </section>
      {children}
    </main>
  );
}
