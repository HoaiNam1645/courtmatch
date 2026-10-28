-- Existing BWF imports have no Courtly creator, booking or venue. They are development seed data,
-- not a third business input channel for the match-recording feature.
UPDATE matches
SET data_source = 'seeded'
WHERE created_by IS NULL
  AND booking_id IS NULL
  AND venue_id IS NULL;
