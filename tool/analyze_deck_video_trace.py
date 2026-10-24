"""Report observed frame-event cadence from a Perfetto gfx/view/video trace.

Install the optional `perfetto` Python package. This measures individual stages,
not game render FPS or end-to-end latency. Counts from nested API wrappers are
kept separate rather than added together.
"""
import argparse
import json
from perfetto.trace_processor import TraceProcessor

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('trace')
parser.add_argument('--output')
parser.add_argument('--encoder-pid', type=int,
                    help='PID from the same capture, if process names are missing')
parser.add_argument('--receiver-pid', type=int,
                    help='PID from the same capture, if process names are missing')
args = parser.parse_args()

query = """
SELECT p.pid, p.name AS process, t.tid, t.name AS thread, s.name AS event,
       count(*) AS count, (max(s.ts)-min(s.ts))/1e9 AS span_seconds,
       CASE WHEN max(s.ts)>min(s.ts) THEN
         (count(*)-1)*1e9/(max(s.ts)-min(s.ts)) END AS events_per_second,
       avg(CASE WHEN s.dur>=0 THEN s.dur END)/1e6 AS average_call_ms
FROM slice s
JOIN thread_track tt ON tt.id=s.track_id
JOIN thread t USING(utid)
JOIN process p USING(upid)
WHERE (
  (p.name LIKE '%projection.gearhead:car' AND (
    s.name LIKE 'CCodecBufferChannel::onWorkDone-%encoder#%' OR
    (t.name LIKE 'Composition%' AND s.name='dequeueBuffer - GraphicBufferSource')
  )) OR
  (p.name LIKE '%headunit%' AND (
    s.name='MediaCodec::queueInputBuffer#native' OR
    s.name='MediaCodec::renderOutputBufferAndRelease#native' OR
    (t.name='RenderThread' AND s.name='dequeueBuffer')
  ))
)
GROUP BY p.pid,p.name,t.tid,t.name,s.name
ORDER BY p.pid,event
"""
if args.encoder_pid is not None:
    query = query.replace("p.name LIKE '%projection.gearhead:car'",
                          f"p.pid={args.encoder_pid}")
    # Short traces can omit thread names as well as process names.
    query = query.replace("t.name LIKE 'Composition%' AND ", "")
if args.receiver_pid is not None:
    query = query.replace("p.name LIKE '%headunit%'", f"p.pid={args.receiver_pid}")

with TraceProcessor(trace=args.trace) as processor:
    rows = [vars(row) for row in processor.query(query)]
    for row in rows:
        # Event timestamps, not nested wrapper counts or call duration, reveal
        # a stream which averages 60Hz but repeatedly stalls for several frames.
        event = row['event'].replace("'", "''")
        times = [r.ts for r in processor.query(f"""
            SELECT s.ts FROM slice s
            JOIN thread_track tt ON tt.id=s.track_id
            JOIN thread t USING(utid) JOIN process p USING(upid)
            WHERE p.pid={row['pid']} AND t.tid={row['tid']} AND s.name='{event}'
            ORDER BY s.ts
        """)]
        gaps = sorted((b-a)/1e6 for a, b in zip(times, times[1:]))
        if gaps:
            row['interval_ms'] = {
                'p50': gaps[int((len(gaps)-1)*0.50)],
                'p95': gaps[int((len(gaps)-1)*0.95)],
                'p99': gaps[int((len(gaps)-1)*0.99)],
                'max': gaps[-1],
                'over_25ms': sum(gap > 25 for gap in gaps),
                'over_40ms': sum(gap > 40 for gap in gaps),
            }
result = json.dumps(rows, indent=2)
if args.output:
    with open(args.output, 'w', encoding='utf-8') as output:
        output.write(result + '\n')
print(result)
