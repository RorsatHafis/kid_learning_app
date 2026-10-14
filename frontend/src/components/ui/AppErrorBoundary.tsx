import { Component, type ErrorInfo, type ReactNode } from "react";
import { Link, useLocation } from "react-router-dom";
import { Button, buttonClasses } from "@/components/ui/Button";
import { useMessages } from "@/i18n/catalog";
import { common } from "@/components/ui/messages";

type Props = { children: ReactNode; resetKey: string };
type State = { failed: boolean };

/**
 * Catches a screen that fails while drawing - an answer the page didn't expect - so the person sees what
 * happened and a way on, instead of the whole app going blank. Moving to another address clears it.
 * Nothing about the error is shown or sent: it may hold a child's data.
 */
class Boundary extends Component<Props, State> {
  state: State = { failed: false };

  static getDerivedStateFromError(): State {
    return { failed: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("A screen failed to draw", error, info.componentStack);
  }

  componentDidUpdate(previous: Props) {
    if (this.state.failed && previous.resetKey !== this.props.resetKey) this.setState({ failed: false });
  }

  render() {
    if (!this.state.failed) return this.props.children;
    return <ScreenFailed onRetry={() => this.setState({ failed: false })} />;
  }
}

function ScreenFailed({ onRetry }: { onRetry: () => void }) {
  const m = useMessages(common);
  return (
    <main className="mx-auto max-w-xl px-4 py-16">
      <section
        role="alert"
        aria-labelledby="screen-failed"
        className="flex flex-col items-center justify-center gap-4 rounded-paper bg-white px-6 py-16 text-center ring-1 ring-line"
      >
        <h1 id="screen-failed" className="font-display text-2xl font-semibold text-tonle-950">
          {m.screenFailedTitle}
        </h1>
        <p className="max-w-sm text-sm text-ink-soft">{m.screenFailedBody}</p>
        <div className="flex flex-wrap justify-center gap-2">
          <Button onClick={onRetry}>{m.tryAgain}</Button>
          <Link to="/" className={buttonClasses("outline", "md")}>
            {m.goToStart}
          </Link>
        </div>
      </section>
    </main>
  );
}

export function AppErrorBoundary({ children }: { children: ReactNode }) {
  const location = useLocation();
  return <Boundary resetKey={location.pathname}>{children}</Boundary>;
}
