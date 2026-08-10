-- The jersey number is what separates two players who share a name. ESPN sends it alongside
-- the stats; it exists here so the BFF can break that tie when matching to the Yahoo read
-- model (two Elias Petterssons on Vancouver, two Matt Murrays, two Connor Murphys).
ALTER TABLE espn_player_stats ADD COLUMN sweater_number INT;
