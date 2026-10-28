-- Stable IDs produced by SeedIds.of("match:match-01" ... "match:match-06").
UPDATE matches
SET data_source = 'seeded'
WHERE id IN (
    'e4c7616e-38d2-3488-a334-6a7802c6f4ec',
    '59d30731-797f-3f00-a9ce-d2f3b33ca4f0',
    '67338b13-5676-3b64-9099-518451b847c3',
    '5d881863-1554-30ec-bc41-f208c2885f20',
    'f405ade8-b861-3d99-a231-8b3f22f83f4b',
    '41076ee1-fb43-30f3-926c-85c9c4fb499c'
);
