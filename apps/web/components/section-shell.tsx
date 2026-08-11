import { SiteHeader } from "@/components/site-header";

export function SectionShell({
  title,
  eyebrow,
  description,
  children,
}: {
  title: string;
  eyebrow: string;
  description: string;
  children: React.ReactNode;
}) {
  return (
    <main className="shell">
      <SiteHeader />
      <section className="panel mb-5 px-6 py-7 sm:px-8">
        <div className="eyebrow">{eyebrow}</div>
        <h1 className="mt-2 font-[var(--font-display)] text-4xl font-semibold tracking-[-0.05em]">
          {title}
        </h1>
        <p className="mt-3 max-w-3xl text-base leading-7 text-ink/72">{description}</p>
      </section>
      {children}
    </main>
  );
}
