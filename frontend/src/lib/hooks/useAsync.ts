import { useCallback, useEffect, useState } from "react";
import { ApiError } from "../api/ApiError";

interface AsyncState<T> {
  data: T | null;
  error: ApiError | Error | null;
  isLoading: boolean;
  refetch: () => void;
}

/** Runs `fetcher` on mount and whenever `deps` change. Not a caching
 * layer — just enough to keep every page from hand-rolling the same
 * loading/error dance. Reach for something heavier if this app ever needs
 * request de-duplication or background refetching. */
export function useAsync<T>(fetcher: () => Promise<T>, deps: unknown[]): AsyncState<T> {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<ApiError | Error | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [tick, setTick] = useState(0);

  const run = useCallback(() => {
    let cancelled = false;
    setIsLoading(true);
    setError(null);
    fetcher()
      .then((result) => {
        if (!cancelled) setData(result);
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err : new Error("Unknown error"));
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick]);

  useEffect(() => run(), [run]);

  return { data, error, isLoading, refetch: () => setTick((t) => t + 1) };
}
