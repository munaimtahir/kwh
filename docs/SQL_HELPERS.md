# Room DAO SQL Helpers

```sql
-- baseline before window
SELECT * FROM meter_readings 
WHERE meter_id = :meterId AND recorded_at < :start 
ORDER BY recorded_at DESC LIMIT 1;

-- earliest in window
SELECT * FROM meter_readings 
WHERE meter_id = :meterId AND recorded_at >= :start AND recorded_at < :end
ORDER BY recorded_at ASC LIMIT 1;

-- latest in window
SELECT * FROM meter_readings 
WHERE meter_id = :meterId AND recorded_at >= :start AND recorded_at < :end
ORDER BY recorded_at DESC LIMIT 1;
```
