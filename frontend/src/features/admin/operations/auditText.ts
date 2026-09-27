/** ACCOUNT_SUSPENDED → "Account suspended". The log stores stable codes; this is only their reading form. */
export function actionLabel(action: string): string {
  const words = action.toLowerCase().split("_");
  return [words[0].charAt(0).toUpperCase() + words[0].slice(1), ...words.slice(1)].join(" ");
}
