import { useState } from "react";
import { useMessages } from "@/i18n/catalog";
import { FormField } from "@/features/auth/FormField";
import { ContentLanguageSwitch, Translated, type ContentLanguage } from "./BilingualFields";
import { khmerCount } from "./bilingual";
import { formMessages } from "./form.messages";
import { TextAreaField } from "./shared";

/**
 * A catalog name - a subject's, skill's, chapter's or curriculum's - and its description, in English and Khmer
 * (LOCALIZATION.md "Content in two languages", V81). The English is required; the Khmer is optional and each part is
 * shown to Khmer readers once written. Switching language keeps what is written in the other.
 */
export function CatalogNameFields({
  nameLabel,
  name,
  onName,
  nameMax = 200,
  description,
  onDescription,
  descriptionLabel = "",
  nameKm,
  onNameKm,
  descriptionKm,
  onDescriptionKm,
  required = true,
}: {
  nameLabel: string;
  name: string;
  onName: (value: string) => void;
  nameMax?: number;
  /** Left out for a name without a description (a curriculum). */
  description?: string;
  onDescription?: (value: string) => void;
  descriptionLabel?: string;
  nameKm: string;
  onNameKm: (value: string) => void;
  descriptionKm?: string;
  onDescriptionKm?: (value: string) => void;
  required?: boolean;
}) {
  const [language, setLanguage] = useState<ContentLanguage>("en");
  const f = useMessages(formMessages);
  const describable = onDescription !== undefined;
  const label = descriptionLabel;
  const counted = khmerCount(describable ? [[name, nameKm], [description, descriptionKm]] : [[name, nameKm]]);
  return (
    <div className="flex flex-col gap-3">
      <ContentLanguageSwitch
        language={language}
        onChange={setLanguage}
        warn={counted.written > 0 && counted.written < counted.needed}
        status={
          counted.needed > 0 && counted.written >= counted.needed
            ? f.catalogKhmerComplete
            : counted.written === 0
              ? f.catalogKhmerNone
              : f.catalogKhmerPartly(counted.written, counted.needed)
        }
      />
      {language === "km" ? (
        <>
          <Translated english={name}>
            <FormField label={f.inKhmer(nameLabel)} lang="km" value={nameKm} maxLength={nameMax} onChange={(e) => onNameKm(e.target.value)} />
          </Translated>
          {describable && (
            <Translated english={description ?? ""}>
              <TextAreaField
                label={f.inKhmer(label)}
                lang="km"
                value={descriptionKm ?? ""}
                maxLength={2000}
                onChange={(e) => onDescriptionKm?.(e.target.value)}
              />
            </Translated>
          )}
        </>
      ) : (
        <>
          <FormField label={nameLabel} value={name} maxLength={nameMax} required={required} onChange={(e) => onName(e.target.value)} />
          {describable && (
            <TextAreaField label={label} value={description ?? ""} maxLength={2000} onChange={(e) => onDescription?.(e.target.value)} />
          )}
        </>
      )}
    </div>
  );
}
