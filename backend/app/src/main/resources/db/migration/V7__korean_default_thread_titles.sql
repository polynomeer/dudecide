-- Default topic and import thread titles moved to Korean. Only titles still equal to the old defaults
-- are renamed, so threads a host already renamed keep their names.
UPDATE topic_thread SET title = '날짜' WHERE category = 'date' AND title = 'Date';
UPDATE topic_thread SET title = '장소' WHERE category = 'location' AND title = 'Location';
UPDATE topic_thread SET title = '숙소' WHERE category = 'lodging' AND title = 'Lodging';
UPDATE topic_thread SET title = '교통' WHERE category = 'transport' AND title = 'Transport';
UPDATE topic_thread SET title = '자유수다' WHERE category = 'free_chat' AND title = 'Free Chat';
UPDATE topic_thread SET title = '가져온 대화: ' || SUBSTRING(title FROM 11) WHERE read_only = TRUE AND title LIKE 'Imported: %';
