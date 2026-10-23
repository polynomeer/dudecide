/** Title row used at the top of every page inside the meeting shell. */
export function MeetingPageHeader({
  kicker,
  title,
  description,
  actions,
}: {
  kicker?: React.ReactNode;
  title: React.ReactNode;
  description?: React.ReactNode;
  actions?: React.ReactNode;
}) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-3">
      <div className="min-w-0">
        {kicker ? <span className="num text-xs font-semibold text-muted">{kicker}</span> : null}
        <h1 className="font-display m-0 mt-1 text-[clamp(1.8rem,3vw,2.4rem)] leading-tight">{title}</h1>
        {description ? <div className="section-copy mt-2 max-w-2xl">{description}</div> : null}
      </div>
      {actions ? <div className="flex flex-wrap gap-2">{actions}</div> : null}
    </div>
  );
}
