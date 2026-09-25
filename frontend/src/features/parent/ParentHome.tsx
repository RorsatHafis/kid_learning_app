import { useState } from "react";
import { Link } from "react-router-dom";
import { Plus, ChevronRight } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { childrenApi } from "@/lib/api/endpoints";
import { LoadingState, ErrorState, EmptyState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { ageFromDateOfBirth } from "@/lib/utils/date";
import { AddChildForm } from "./AddChildForm";
import type { Child } from "@/lib/api/types";

export function ParentHome() {
  const { data: children, error, isLoading, refetch } = useAsync(() => childrenApi.list(), []);
  const [isAdding, setIsAdding] = useState(false);

  return (
    <div className="flex flex-col gap-8">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-extrabold text-ink">Your children</h1>
          <p className="text-sm text-muted">See how each child is doing and jump into their learning.</p>
        </div>
        {children && children.length > 0 && !isAdding && (
          <Button size="md" onClick={() => setIsAdding(true)}>
            <Plus size={18} /> Add child
          </Button>
        )}
      </div>

      {isAdding && (
        <AddChildForm
          onCreated={() => {
            setIsAdding(false);
            refetch();
          }}
          onCancel={() => setIsAdding(false)}
        />
      )}

      {isLoading && <LoadingState label="Loading your family…" />}
      {error && <ErrorState error={error} onRetry={refetch} />}

      {children && children.length === 0 && !isAdding && (
        <EmptyState
          title="No children yet"
          description="Add your first child to start their Bookeyond Kid learning journey."
          action={
            <Button size="md" onClick={() => setIsAdding(true)} className="mt-2">
              <Plus size={18} /> Add your first child
            </Button>
          }
        />
      )}

      {children && children.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {children.map((child) => (
            <ChildCard key={child.id} child={child} />
          ))}
        </div>
      )}
    </div>
  );
}

function ChildCard({ child }: { child: Child }) {
  return (
    <Link
      to={`/parent/children/${child.id}`}
      className="flex items-center justify-between gap-4 rounded-3xl border-[1.5px] border-hairline bg-white p-6 shadow-soft transition-transform hover:-translate-y-0.5"
    >
      <div className="flex items-center gap-4">
        <div className="flex h-14 w-14 items-center justify-center rounded-full bg-brand-blueTint text-lg font-bold text-brand-blue">
          {child.displayName.charAt(0).toUpperCase()}
        </div>
        <div>
          <p className="font-bold text-ink">{child.displayName}</p>
          <p className="text-sm text-muted">
            Age {ageFromDateOfBirth(child.dateOfBirth)} · {child.status === "ACTIVE" ? "Active" : "Archived"}
          </p>
        </div>
      </div>
      <ChevronRight className="text-muted" />
    </Link>
  );
}
