import { ArrowDownToLine, Hash, Link2, ListOrdered, Pencil, Pointer, ThumbsUp, type LucideIcon } from "lucide-react";
import type { QuestionType } from "@/lib/api/types";

/**
 * A picture of what each kind of question asks a child to do - the same one in the child's activity intro
 * and in the Studio's "what should children do?" choice, so authors and children see one language.
 */
export const KIND_PICTURE: Record<QuestionType, LucideIcon> = {
  MULTIPLE_CHOICE: Pointer,
  TRUE_FALSE: ThumbsUp,
  NUMERIC: Hash,
  SHORT_ANSWER: Pencil,
  ORDERING: ListOrdered,
  MATCHING: Link2,
  SORTING: ArrowDownToLine,
};
