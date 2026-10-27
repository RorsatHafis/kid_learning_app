-- F1c (2026-10-27): the parent demo page (/try/parent, a fictional sample child) has its own catalog; its words are as
-- editable as the other public pages'. Only the allowed list changes - SiteCopyService.NAMESPACES holds the same set.
ALTER TABLE site_copy DROP CONSTRAINT ck_site_copy_namespace;
ALTER TABLE site_copy ADD CONSTRAINT ck_site_copy_namespace CHECK (namespace IN ('features/marketing',
    'features/marketing/trust', 'features/marketing/try', 'features/marketing/parentDemo'));
