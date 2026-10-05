alter table public.songs alter column created_by set default auth.uid();
