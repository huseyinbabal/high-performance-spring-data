-- Seed: 500 venues · 100k events · 300k ticket types · 200k customers · 1M orders (~20 s).
-- Small tables hide every problem this course is about, so we start with real volume.
select setseed(0.42);

insert into venue (id, name, city, capacity)
select g,
       c.city || ' ' || (array['Arena', 'Sahne', 'Açıkhava', 'Kongre Merkezi', 'Stadyum',
                               'Kültür Merkezi', 'Performans Salonu'])[1 + g % 7] || ' ' || g,
       c.city,
       (array[800, 1500, 3000, 5000, 12000, 25000, 45000])[1 + g % 7]
from generate_series(1, 500) g
cross join lateral (
    -- İstanbul is over-represented on purpose: real traffic is skewed
    select (array['İstanbul', 'İstanbul', 'İstanbul', 'İstanbul', 'İstanbul', 'İstanbul',
                  'Ankara', 'Ankara', 'Ankara', 'İzmir', 'İzmir', 'İzmir',
                  'Antalya', 'Bursa', 'Eskişehir', 'Adana', 'Gaziantep',
                  'Trabzon', 'Muğla', 'Diyarbakır'])[1 + (g * 7) % 20] as city
) c;

insert into event (id, name, artist, category, description, venue_id, starts_at, status)
select g,
       a.artist || ' — ' || a.tour,
       a.artist,
       a.category,
       a.s1 || ' ' || a.s2 || ' ' || a.s3,
       a.venue_id,
       now() - interval '45 days' + random() * interval '400 days',
       case when random() < 0.90 then 'ON_SALE' when random() < 0.7 then 'SOLD_OUT' else 'CANCELLED' end
from generate_series(1, 100000) g
cross join lateral (
    select (array['Gece Yolcuları', 'Mor Köprü', 'Kuzey Rüzgârı', 'Çınar Altı', 'Şehir Işıkları',
                  'Deniz Feneri Orkestrası', 'Yağmur Sonrası', 'Kırmızı Balon', 'Son Vapur', 'Ada Sahili',
                  'Güneşin Çocukları', 'Sessiz Liman', 'Boğaz Senfonisi', 'Ay Tutulması', 'Mavi Kuzgun',
                  'Üç Tel', 'Öykü Trio', 'Taş Plak', 'Rüya Atölyesi', 'Anadolu Ekspresi',
                  'Yıldız Tozu', 'Poyraz', 'İkinci Perde', 'Karanfil Sokak', 'Lodos Kumpanya',
                  'Efe Kaya', 'Zeynep Arın', 'Deniz Akça', 'Selin Yücel', 'Barış Tuna'])
               [1 + floor(random() * 30 + g * 0)::int] as artist,
           (array['Yaz Turnesi', 'Akustik Gece', 'Senfonik Konser', 'Büyük Buluşma', 'Açıkhava Konseri',
                  'Veda Turnesi', 'Yeni Albüm Lansmanı', 'Canlı Performans', '10. Yıl Özel', 'Tek Gece'])
               [1 + floor(random() * 10 + g * 0)::int] as tour,
           (array['rock', 'pop', 'jazz', 'electronic', 'classical', 'theatre', 'standup', 'festival'])
               [1 + floor(random() * 8 + g * 0)::int] as category,
           (array['An unforgettable live night with a full band and special guests.',
                  'An intimate acoustic set under the open sky.',
                  'A symphony orchestra joins the stage for a one-off performance.',
                  'The long-awaited anniversary tour finally arrives.',
                  'A high-energy show with a brand new stage design.',
                  'A farewell tour through the greatest hits.',
                  'The new album played start to finish for the first time.',
                  'A late-night session for true fans of the genre.'])
               [1 + floor(random() * 8 + g * 0)::int] as s1,
           (array['Doors open two hours before the show.',
                  'Limited VIP packages include a backstage meet and greet.',
                  'Family friendly, children under seven enter free.',
                  'Standing floor tickets sell out first, so be quick.',
                  'Seated tribune tickets offer the best view of the stage.',
                  'Food trucks and a craft market fill the venue garden.'])
               [1 + floor(random() * 6 + g * 0)::int] as s2,
           (array['Rain or shine, the concert goes on.',
                  'Free shuttle buses run from the city centre.',
                  'Wheelchair accessible seating is available on request.',
                  'Parking is limited, public transport is recommended.',
                  'Recording and photography are welcome.',
                  'Every ticket plants a tree with our partner foundation.'])
               [1 + floor(random() * 6 + g * 0)::int] as s3,
           1 + floor(random() * 500 + g * 0)::bigint as venue_id
) a;

-- three ticket types per event: ids 3n-2, 3n-1, 3n
insert into ticket_type (id, event_id, name, price, capacity, sold)
select (e.id - 1) * 3 + k.n,
       e.id,
       k.name,
       round((p.base * k.mult)::numeric, 2),
       k.capacity,
       floor(random() * k.capacity * 0.9)::int
from event e
cross join lateral (select 150 + random() * 650 + e.id * 0 as base) p
cross join (values (1, 'VIP', 3.0, 100), (2, 'Saha', 1.4, 1000), (3, 'Tribün', 1.0, 2000)) as k(n, name, mult, capacity);

insert into customer (id, email, full_name, city)
select g,
       'user' || g || '@example.com',
       (array['Ayşe', 'Mehmet', 'Elif', 'Can', 'Zeynep', 'Emre', 'Selin', 'Burak', 'Deniz', 'Ece'])[1 + g % 10]
           || ' ' ||
       (array['Yılmaz', 'Kaya', 'Demir', 'Şahin', 'Çelik', 'Öztürk', 'Aydın', 'Arslan', 'Doğan'])[1 + g % 9],
       (array['İstanbul', 'Ankara', 'İzmir', 'Antalya', 'Bursa', 'Eskişehir', 'Adana'])[1 + g % 7]
from generate_series(1, 200000) g;

-- 1M orders, ids ascending with time (so "newest first" = "highest id first").
-- Ticket types 1-3 (event 1) are left untouched: that event is tonight's flash sale.
insert into orders (id, customer_id, ticket_type_id, quantity, total, status, created_at)
select g,
       1 + floor(random() * 200000)::bigint,
       4 + floor(random() * 299997)::bigint,
       q.n,
       round((q.n * (150 + random() * 650))::numeric, 2),
       case when random() < 0.97 then 'PAID' else 'FAILED' end,
       now() - (1000000 - g) * interval '30 seconds'
from generate_series(1, 1000000) g
cross join lateral (select 1 + floor(random() * 4 + g * 0)::int as n) q;

-- Event 1: the on-sale event every purchase demo hammers.
update event
set name = 'Gece Yolcuları — Büyük Buluşma', artist = 'Gece Yolcuları', category = 'rock',
    status = 'ON_SALE', starts_at = now() + interval '60 days', venue_id = 1
where id = 1;
update ticket_type set capacity = 1000, sold = 0 where event_id = 1;

analyze;
create table seed_done as select 1 as ok;   -- the compose healthcheck waits for this
