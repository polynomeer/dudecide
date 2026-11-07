import Link from "next/link";
import { StandaloneShell } from "@/components/standalone-shell";

export default function NotFound() {
  return (
    <StandaloneShell width="max-w-[720px]">
      <span className="num text-xs font-semibold text-muted">404</span>
      <h1 className="font-display m-0 text-[clamp(1.8rem,4vw,2.4rem)]">페이지를 찾을 수 없어요</h1>
      <p className="section-copy m-0">주소가 바뀌었거나 삭제된 페이지예요. 링크를 받았다면 보낸 사람에게 다시 요청해 주세요.</p>
      <div>
        <Link href="/" className="action-primary">
          처음으로
        </Link>
      </div>
    </StandaloneShell>
  );
}
