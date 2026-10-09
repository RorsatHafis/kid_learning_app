import { useAsync } from "@/lib/hooks/useAsync";
import { authApi } from "@/lib/api/endpoints";

/** Where the owner's privacy notice and terms are, and their version; all null until configured (Y15). */
export function useLegal() {
  return useAsync(() => authApi.legal(), []);
}
