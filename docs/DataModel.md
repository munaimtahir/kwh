# DataModel.md
## Tables
### meters
- id (PK, auto)
- name (TEXT, not null)
- reminder_enabled (BOOLEAN)
- reminder_frequency_days (INT)
- reminder_hour (INT)
- reminder_minute (INT)

### meter_readings
- id (PK, auto)
- meter_id (FK → meters.id, CASCADE)
- value (REAL, not null)
- notes (TEXT, nullable)
- recorded_at (LONG epoch millis)

## Derived Views
- Latest reading per meter (LEFT JOIN; nullable if none).
