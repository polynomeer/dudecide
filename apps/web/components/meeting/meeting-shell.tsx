"use client";

import Link from "next/link";
import type { Route } from "next";
import { usePathname, useRouter } from "next/navigation";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { LogoMark } from "@/components/brand/logo";
import { ThemeToggle } from "@/components/theme/theme-toggle";
import { apiClient } from "@/lib/api/client";
import { avatarColor, initialOf, normalizeDecisionStatus } from "@/lib/display";
import { useMeetingMembers } from "@/lib/members";
import { useCurrentActor } from "@/lib/session";

type NavItem = {
  href: string;
  label: string;
  icon: React.ReactNode;
  mobileLabel?: string;
  badge?: number;
  exact?: boolean;
};

const iconProps = {
  width: 18,
  height: 18,
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 2,
  strokeLinecap: "round" as const,
  strokeLinejoin: "round" as const,
  "aria-hidden": true,
};

const icons = {
  home: (
    <svg {...iconProps}>
      <path d="M4 11 12 4l8 7v9h-5v-6H9v6H4z" />
    </svg>
  ),
  decisions: (
    <svg {...iconProps}>
      <rect x="4" y="4" width="16" height="16" rx="3" />
      <path d="m8 12 3 3 5-6" />
    </svg>
  ),
  report: (
    <svg {...iconProps}>
      <path d="M7 3h7l5 5v13H7z" />
      <path d="M14 3v5h5M10 13h6M10 17h6" />
    </svg>
  ),
  tasks: (
    <svg {...iconProps}>
      <path d="M9 6h11M9 12h11M9 18h11" />
      <path d="m3.5 6 1.5 1.5L7.5 5M3.5 12l1.5 1.5L7.5 11M3.5 18l1.5 1.5L7.5 17" />
    </svg>
  ),
  bell: (
    <svg {...iconProps} width={20} height={20}>
      <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
      <path d="M10 21h4" />
    </svg>
  ),
  search: (
    <svg {...iconProps}>
      <circle cx="11" cy="11" r="7" />
      <path d="m20 20-3.5-3.5" />
    </svg>
  ),
};

/** Routes that need the full width (W-3 three-pane view) hide the sidebar. */
function isFocusRoute(pathname: string, base: string) {
  return pathname.startsWith(`${base}/decisions`);
}

export function MeetingShell({ meetingId, children }: { meetingId: string; children: React.ReactNode }) {
  const pathname = usePathname() ?? "";
  const router = useRouter();
  const base = `/meetings/${meetingId}`;
  const [query, setQuery] = useState("");
  const { data: actor } = useCurrentActor();
  const { data: meeting } = useQuery({
    queryKey: ["meeting", meetingId],
    queryFn: () => apiClient.getMeeting(meetingId),
  });
  const { data: members } = useMeetingMembers(meetingId);
  const { data: decisions } = useQuery({
    queryKey: ["meeting-decisions", meetingId],
    queryFn: () => apiClient.listDecisions(meetingId),
  });

  const reviewCount = decisions?.filter((decision) => normalizeDecisionStatus(decision.status) === "proposed").length ?? 0;
  const navItems: NavItem[] = [
    { href: base, label: "홈", icon: icons.home, exact: true },
    { href: `${base}/decisions`, label: "결정 보드", mobileLabel: "결정", icon: icons.decisions, badge: reviewCount || undefined },
    { href: `${base}/reports/latest`, label: "리포트", icon: icons.report },
    { href: `${base}/tasks`, label: "할 일", icon: icons.tasks },
  ];
  const focus = isFocusRoute(pathname, base);

  function submitSearch(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmed = query.trim();
    router.push(`${base}/search${trimmed ? `?q=${encodeURIComponent(trimmed)}` : ""}` as Route);
  }

  return (
    <div className="min-h-screen bg-bg text-ink">
      <header className="border-b border-line bg-surface">
        <div className="mx-auto flex max-w-[1440px] flex-wrap items-center gap-x-3 gap-y-2 px-4 py-2.5 sm:px-6 lg:gap-4">
          <Link href="/" aria-label="dudecide 첫 화면" className="flex min-h-[44px] items-center gap-2">
            <LogoMark size={28} />
            <span className="font-display hidden text-[19px] sm:inline">dudecide</span>
          </Link>
          <Link
            href={base as Route}
            className="flex min-h-[40px] min-w-0 max-w-[16rem] flex-1 items-center truncate rounded-[10px] border border-line bg-surface-2 px-3 text-sm font-bold lg:flex-none"
          >
            <span className="truncate">{meeting?.title ?? "모임"}</span>
          </Link>
          <form role="search" onSubmit={submitSearch} className="relative order-last w-full lg:order-none lg:w-auto lg:max-w-[460px] lg:flex-[1_1_240px]">
            <label htmlFor="meeting-search" className="sr-only">
              모임에서 검색
            </label>
            <input
              id="meeting-search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="메시지, 결정, 할 일 검색"
              className="h-11 w-full rounded-xl border border-line bg-surface-2 pl-10 pr-3.5 text-sm text-ink outline-none placeholder:text-muted focus:border-accent"
            />
            <span className="pointer-events-none absolute left-[13px] top-[13px] text-muted">{icons.search}</span>
          </form>
          <div className="ml-auto flex items-center gap-1.5 lg:gap-2">
            <ThemeToggle className="lg:!h-11 lg:!w-11" />
            <Link
              href={`${base}/notifications` as Route}
              aria-label="알림"
              className="relative flex h-10 w-10 items-center justify-center rounded-xl border border-line bg-surface text-ink lg:h-11 lg:w-11"
            >
              {icons.bell}
            </Link>
            <span
              title={actor?.displayName ?? undefined}
              aria-label={actor ? `내 계정 · ${actor.displayName}` : "내 계정"}
              className="flex h-10 w-10 items-center justify-center rounded-full text-sm font-bold text-[#15171C] lg:h-11 lg:w-11"
              style={{ background: avatarColor(actor?.displayName ?? "?") }}
            >
              {initialOf(actor?.displayName)}
            </span>
          </div>
        </div>
      </header>

      {focus ? null : (
        <nav aria-label="토픽" className="flex gap-1.5 overflow-x-auto border-b border-line bg-surface px-4 py-2 [scrollbar-width:none] lg:hidden [&::-webkit-scrollbar]:hidden">
          {meeting?.threads.map((thread) => {
            const href = `${base}/threads/${thread.id}`;
            const active = pathname === href;
            return (
              <Link
                key={thread.id}
                href={href as Route}
                aria-current={active ? "page" : undefined}
                className={`inline-flex h-9 flex-none items-center whitespace-nowrap rounded-full border px-3 text-[13px] font-semibold ${
                  active ? "border-ink bg-ink text-on-ink" : "border-line bg-surface text-ink"
                }`}
              >
                # {thread.title}
              </Link>
            );
          })}
          <Link
            href={`${base}/import` as Route}
            className="inline-flex h-9 flex-none items-center whitespace-nowrap rounded-full border border-dashed border-accent/50 px-3 text-[13px] font-semibold text-accent-text"
          >
            대화 가져오기
          </Link>
        </nav>
      )}

      <div className="mx-auto flex max-w-[1440px] flex-wrap items-start gap-6 px-4 pb-28 pt-5 sm:px-6 lg:pb-6 lg:pt-6">
        {focus ? null : (
          <aside aria-label="모임 메뉴" className="hidden max-w-[260px] flex-[1_1_210px] flex-col gap-[18px] lg:flex">
            <nav className="flex flex-col gap-0.5">
              {navItems.map((item) => {
                const active = item.exact ? pathname === item.href : pathname.startsWith(item.href);
                return (
                  <SideLink key={item.href} href={item.href} active={active}>
                    <span className={active ? "text-accent" : ""}>{item.icon}</span>
                    {item.label}
                    {item.badge ? <span className="chip-voting ml-auto">{item.badge}</span> : null}
                  </SideLink>
                );
              })}
            </nav>

            {meeting?.threads.length ? (
              <div className="flex flex-col gap-0.5">
                <span className="mono-label px-3 pb-1.5">토픽</span>
                {meeting.threads.map((thread) => {
                  const href = `${base}/threads/${thread.id}`;
                  return (
                    <SideLink key={thread.id} href={href} active={pathname === href}>
                      <span className="truncate"># {thread.title}</span>
                      {thread.openVoteCount ? (
                        <span className="num ml-auto text-[11px] text-accent-text">{thread.openVoteCount}</span>
                      ) : thread.messageCount ? (
                        <span className="num ml-auto text-[11px] text-muted">{thread.messageCount}</span>
                      ) : null}
                    </SideLink>
                  );
                })}
              </div>
            ) : null}

            <Link
              href={`${base}/import` as Route}
              className="flex min-h-[46px] items-center justify-center rounded-xl border-[1.5px] border-dashed border-accent/50 text-sm font-semibold text-accent-text hover:bg-accent-soft"
            >
              단톡방 대화 가져오기
            </Link>

            {meeting ? (
              <div className="flex flex-col gap-2 px-3">
                <span className="mono-label">
                  멤버 {meeting.memberCount}
                  {meeting.expectedHeadcount ? ` / ${meeting.expectedHeadcount}` : ""}
                </span>
                {members?.length ? (
                  <ul aria-label="참여한 멤버" className="m-0 flex list-none flex-wrap gap-1 p-0">
                    {members.slice(0, 11).map((member) => (
                      <li
                        key={member.id}
                        title={member.displayName}
                        aria-label={member.displayName}
                        className="flex h-[30px] w-[30px] items-center justify-center rounded-full text-xs font-bold text-[#15171C]"
                        style={{ background: avatarColor(member.displayName) }}
                      >
                        {initialOf(member.displayName)}
                      </li>
                    ))}
                    {members.length > 11 ? (
                      <li className="num flex h-[30px] items-center px-1 text-[11px] text-muted">+{members.length - 11}</li>
                    ) : null}
                    {meeting.expectedHeadcount && meeting.expectedHeadcount > members.length ? (
                      <li
                        aria-label={`아직 ${meeting.expectedHeadcount - members.length}명 남았어요`}
                        className="num flex h-[30px] min-w-[30px] items-center justify-center rounded-full border-[1.5px] border-dashed border-muted/60 px-1 text-[11px] text-muted"
                      >
                        +{meeting.expectedHeadcount - members.length}
                      </li>
                    ) : null}
                  </ul>
                ) : null}
              </div>
            ) : null}
          </aside>
        )}

        <main className="flex min-w-0 flex-[999_1_640px] flex-col gap-5">{children}</main>
      </div>

      <nav
        aria-label="모임 메뉴"
        className="fixed inset-x-0 bottom-0 z-40 grid grid-cols-4 border-t border-line bg-surface px-2 pb-[max(10px,env(safe-area-inset-bottom))] lg:hidden"
      >
        {navItems.map((item) => {
          const active = item.exact ? pathname === item.href : pathname.startsWith(item.href);
          return (
            <Link
              key={item.href}
              href={item.href as Route}
              aria-current={active ? "page" : undefined}
              className={`relative flex min-h-[60px] flex-col items-center justify-center gap-1 text-[11px] ${
                active ? "font-bold text-ink shadow-[inset_0_2px_0_rgb(var(--ink))]" : "font-semibold text-muted"
              }`}
            >
              <span className={active ? "text-accent" : ""}>{item.icon}</span>
              {item.mobileLabel ?? item.label}
              {item.badge ? (
                <span className="num absolute right-[calc(50%-22px)] top-2 rounded-full bg-accent px-1.5 text-[10px] font-semibold text-on-accent">
                  {item.badge}
                </span>
              ) : null}
            </Link>
          );
        })}
      </nav>
    </div>
  );
}

function SideLink({ href, active, children }: { href: string; active: boolean; children: React.ReactNode }) {
  return (
    <Link
      href={href as Route}
      aria-current={active ? "page" : undefined}
      className={`flex min-h-[42px] items-center gap-2.5 rounded-xl px-3 text-sm font-semibold transition ${
        active ? "bg-surface text-ink ring-1 ring-line" : "text-ink/80 hover:bg-surface-2 hover:text-ink"
      }`}
    >
      {children}
    </Link>
  );
}
