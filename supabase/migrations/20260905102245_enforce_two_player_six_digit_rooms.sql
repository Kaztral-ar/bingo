-- Enforce the online Bingo room contract:
-- * room codes are exactly six decimal digits
-- * a room can contain at most two players, including concurrent joins

ALTER TABLE public.rooms
  DROP CONSTRAINT IF EXISTS rooms_room_code_six_digits;

ALTER TABLE public.rooms
  ADD CONSTRAINT rooms_room_code_six_digits
  CHECK (room_code ~ '^[0-9]{6}$');

CREATE OR REPLACE FUNCTION public.enforce_bingo_room_player_limit()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  PERFORM 1
  FROM public.rooms
  WHERE room_code = NEW.room_code
  FOR UPDATE;

  IF (SELECT count(*) FROM public.room_players WHERE room_code = NEW.room_code) >= 2
     AND NOT EXISTS (
       SELECT 1
       FROM public.room_players
       WHERE room_code = NEW.room_code
         AND uid = NEW.uid
     ) THEN
    RAISE EXCEPTION 'Room full: only 2 players are allowed';
  END IF;

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS bingo_room_player_limit ON public.room_players;

CREATE TRIGGER bingo_room_player_limit
BEFORE INSERT ON public.room_players
FOR EACH ROW
EXECUTE FUNCTION public.enforce_bingo_room_player_limit();

REVOKE ALL ON FUNCTION public.enforce_bingo_room_player_limit() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.enforce_bingo_room_player_limit() TO service_role;
