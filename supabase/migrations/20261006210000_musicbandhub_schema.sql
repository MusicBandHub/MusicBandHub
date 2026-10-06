-- MusicBandHub: complete Supabase schema
create extension if not exists pgcrypto;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null default '',
  created_at timestamptz not null default now()
);

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.profiles(id, display_name)
  values (new.id, coalesce(new.raw_user_meta_data->>'display_name',''))
  on conflict (id) do update
    set display_name = excluded.display_name;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
after insert on auth.users
for each row execute function public.handle_new_user();

create table if not exists public.bands (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  description text not null default '',
  owner_id uuid not null references public.profiles(id) on delete restrict,
  invite_code text not null unique default upper(substr(md5(random()::text || clock_timestamp()::text),1,8)),
  created_at timestamptz not null default now()
);

create table if not exists public.band_members (
  id uuid primary key default gen_random_uuid(),
  band_id uuid not null references public.bands(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  role text not null default 'MEMBER' check (role in ('OWNER','ADMIN','MEMBER')),
  created_at timestamptz not null default now(),
  unique(band_id,user_id)
);

create or replace function public.add_band_owner()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.band_members(band_id,user_id,role)
  values(new.id,new.owner_id,'OWNER')
  on conflict (band_id,user_id) do update set role='OWNER';
  return new;
end;
$$;

drop trigger if exists after_band_created on public.bands;
create trigger after_band_created
after insert on public.bands
for each row execute function public.add_band_owner();

create table if not exists public.songs (
  id uuid primary key default gen_random_uuid(),
  band_id uuid not null references public.bands(id) on delete cascade,
  title text not null,
  status text not null default 'IDEA' check (status in ('IDEA','DRAFT','ARRANGEMENT','READY','ARCHIVED')),
  bpm integer check (bpm is null or (bpm between 20 and 300)),
  key_signature text,
  time_signature text,
  structure text not null default '',
  lyrics text not null default '',
  chords text not null default '',
  notes text not null default '',
  created_by uuid not null references public.profiles(id) on delete restrict,
  updated_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.drafts (
  id uuid primary key default gen_random_uuid(),
  band_id uuid not null references public.bands(id) on delete cascade,
  song_id uuid references public.songs(id) on delete set null,
  title text not null,
  description text not null default '',
  type text not null default 'IDEA',
  created_by uuid not null references public.profiles(id) on delete restrict,
  created_at timestamptz not null default now()
);

create table if not exists public.rehearsals (
  id uuid primary key default gen_random_uuid(),
  band_id uuid not null references public.bands(id) on delete cascade,
  title text not null,
  date date not null,
  start_time time not null,
  end_time time,
  location text not null default '',
  created_by uuid not null references public.profiles(id) on delete restrict,
  created_at timestamptz not null default now()
);

create table if not exists public.rehearsal_attendees (
  id uuid primary key default gen_random_uuid(),
  rehearsal_id uuid not null references public.rehearsals(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  status text not null check (status in ('GOING','MAYBE','NOT_GOING')),
  created_at timestamptz not null default now(),
  unique(rehearsal_id,user_id)
);

create table if not exists public.song_files (
  id uuid primary key default gen_random_uuid(),
  song_id uuid not null references public.songs(id) on delete cascade,
  band_id uuid not null references public.bands(id) on delete cascade,
  file_name text not null,
  storage_path text not null,
  file_type text not null,
  file_size bigint not null default 0,
  version integer not null default 1,
  uploaded_by uuid not null references public.profiles(id) on delete restrict,
  created_at timestamptz not null default now()
);

create table if not exists public.activity_log (
  id uuid primary key default gen_random_uuid(),
  band_id uuid not null references public.bands(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete restrict,
  entity_type text not null,
  action text not null,
  entity_id uuid,
  old_value text,
  new_value text,
  created_at timestamptz not null default now()
);

create table if not exists public.invitations (
  id uuid primary key default gen_random_uuid(),
  band_id uuid not null references public.bands(id) on delete cascade,
  email text not null,
  invited_by uuid not null references public.profiles(id) on delete restrict,
  created_at timestamptz not null default now(),
  unique(band_id,email)
);

create or replace function public.is_band_member(p_band uuid, p_user uuid default auth.uid())
returns boolean
language sql stable security definer
set search_path = public
as $$
  select exists(select 1 from public.band_members where band_id=p_band and user_id=p_user);
$$;

create or replace function public.is_band_admin(p_band uuid, p_user uuid default auth.uid())
returns boolean
language sql stable security definer
set search_path = public
as $$
  select exists(select 1 from public.band_members where band_id=p_band and user_id=p_user and role in ('OWNER','ADMIN'));
$$;

create or replace function public.join_band(code text)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare b uuid;
begin
  if auth.uid() is null then raise exception 'not_authenticated'; end if;
  select id into b from public.bands where upper(invite_code)=upper(trim(code)) limit 1;
  if b is null then raise exception 'invalid_invite_code'; end if;
  insert into public.band_members(band_id,user_id,role)
  values(b,auth.uid(),'MEMBER')
  on conflict (band_id,user_id) do nothing;
  return b;
end;
$$;

create or replace function public.accept_invitation(inv uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare b uuid; invited_email text; current_email text;
begin
  if auth.uid() is null then raise exception 'not_authenticated'; end if;
  select band_id,email into b,invited_email from public.invitations where id=inv;
  if b is null then raise exception 'invalid_invitation'; end if;
  select lower(email) into current_email from auth.users where id=auth.uid();
  if lower(invited_email) <> current_email then raise exception 'invitation_email_mismatch'; end if;
  insert into public.band_members(band_id,user_id,role)
  values(b,auth.uid(),'MEMBER')
  on conflict (band_id,user_id) do nothing;
  delete from public.invitations where id=inv;
  return b;
end;
$$;

create or replace function public.decline_invitation(inv uuid)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare deleted_count integer;
begin
  delete from public.invitations
  where id=inv
    and lower(email)=(select lower(email) from auth.users where id=auth.uid());
  get diagnostics deleted_count = row_count;
  return deleted_count > 0;
end;
$$;

alter table public.profiles enable row level security;
alter table public.bands enable row level security;
alter table public.band_members enable row level security;
alter table public.songs enable row level security;
alter table public.drafts enable row level security;
alter table public.rehearsals enable row level security;
alter table public.rehearsal_attendees enable row level security;
alter table public.song_files enable row level security;
alter table public.activity_log enable row level security;
alter table public.invitations enable row level security;

drop policy if exists profiles_select on public.profiles;
create policy profiles_select on public.profiles for select to authenticated using (true);
drop policy if exists profiles_update_self on public.profiles;
create policy profiles_update_self on public.profiles for update to authenticated using (id=auth.uid()) with check (id=auth.uid());

drop policy if exists bands_select_member on public.bands;
create policy bands_select_member on public.bands for select to authenticated using (public.is_band_member(id));
drop policy if exists bands_insert_owner on public.bands;
create policy bands_insert_owner on public.bands for insert to authenticated with check (owner_id=auth.uid());
drop policy if exists bands_update_admin on public.bands;
create policy bands_update_admin on public.bands for update to authenticated using (public.is_band_admin(id)) with check (public.is_band_admin(id));
drop policy if exists bands_delete_owner on public.bands;
create policy bands_delete_owner on public.bands for delete to authenticated using (owner_id=auth.uid());

drop policy if exists members_select on public.band_members;
create policy members_select on public.band_members for select to authenticated using (public.is_band_member(band_id));
drop policy if exists members_insert_self on public.band_members;
create policy members_insert_self on public.band_members for insert to authenticated with check (user_id=auth.uid());
drop policy if exists members_update_admin on public.band_members;
create policy members_update_admin on public.band_members for update to authenticated using (public.is_band_admin(band_id)) with check (public.is_band_admin(band_id));
drop policy if exists members_delete_admin on public.band_members;
create policy members_delete_admin on public.band_members for delete to authenticated using (public.is_band_admin(band_id));

drop policy if exists songs_all_member on public.songs;
create policy songs_all_member on public.songs for all to authenticated using (public.is_band_member(band_id)) with check (public.is_band_member(band_id));

drop policy if exists drafts_all_member on public.drafts;
create policy drafts_all_member on public.drafts for all to authenticated using (public.is_band_member(band_id)) with check (public.is_band_member(band_id));

drop policy if exists rehearsals_all_member on public.rehearsals;
create policy rehearsals_all_member on public.rehearsals for all to authenticated using (public.is_band_member(band_id)) with check (public.is_band_member(band_id));

drop policy if exists attendees_select_member on public.rehearsal_attendees;
create policy attendees_select_member on public.rehearsal_attendees for select to authenticated using (exists(select 1 from public.rehearsals r where r.id=rehearsal_id and public.is_band_member(r.band_id)));
drop policy if exists attendees_insert_self on public.rehearsal_attendees;
create policy attendees_insert_self on public.rehearsal_attendees for insert to authenticated with check (user_id=auth.uid());
drop policy if exists attendees_update_self on public.rehearsal_attendees;
create policy attendees_update_self on public.rehearsal_attendees for update to authenticated using (user_id=auth.uid()) with check (user_id=auth.uid());

drop policy if exists files_all_member on public.song_files;
create policy files_all_member on public.song_files for all to authenticated using (public.is_band_member(band_id)) with check (public.is_band_member(band_id));

drop policy if exists activity_select_member on public.activity_log;
create policy activity_select_member on public.activity_log for select to authenticated using (public.is_band_member(band_id));
drop policy if exists activity_insert_member on public.activity_log;
create policy activity_insert_member on public.activity_log for insert to authenticated with check (user_id=auth.uid() and public.is_band_member(band_id));

drop policy if exists invites_select_related on public.invitations;
create policy invites_select_related on public.invitations for select to authenticated using (
  lower(email)=(select lower(email) from auth.users where id=auth.uid())
  or public.is_band_admin(band_id)
);
drop policy if exists invites_insert_admin on public.invitations;
create policy invites_insert_admin on public.invitations for insert to authenticated with check (public.is_band_admin(band_id) and invited_by=auth.uid());
drop policy if exists invites_delete_related on public.invitations;
create policy invites_delete_related on public.invitations for delete to authenticated using (
  public.is_band_admin(band_id)
  or lower(email)=(select lower(email) from auth.users where id=auth.uid())
);

insert into storage.buckets(id,name,public)
values('band-files','band-files',false)
on conflict (id) do nothing;

drop policy if exists band_files_select on storage.objects;
create policy band_files_select on storage.objects for select to authenticated using (
  bucket_id='band-files' and public.is_band_member((storage.foldername(name))[2]::uuid)
);
drop policy if exists band_files_insert on storage.objects;
create policy band_files_insert on storage.objects for insert to authenticated with check (
  bucket_id='band-files' and public.is_band_member((storage.foldername(name))[2]::uuid)
);
drop policy if exists band_files_delete on storage.objects;
create policy band_files_delete on storage.objects for delete to authenticated using (
  bucket_id='band-files' and public.is_band_member((storage.foldername(name))[2]::uuid)
);

grant execute on function public.join_band(text) to authenticated;
grant execute on function public.accept_invitation(uuid) to authenticated;
grant execute on function public.decline_invitation(uuid) to authenticated;
