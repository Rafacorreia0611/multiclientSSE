if (!exists("input_path")) {
    input_path = "benchmarkSSE/results/data/search_latency_by_replicas_summary.tsv"
}

if (!exists("output_dir")) {
    output_dir = "benchmarkSSE/plots"
}

if (!exists("bucket_specs")) {
    bucket_specs = "8:12 80:120 800:1200 8000:12000"
}

if (!exists("bucket_labels")) {
    bucket_labels = "8-12 80-120 800-1200 8000-12000"
}

bucket_count = words(bucket_specs)

set datafile separator "\t"
set terminal pngcairo size 980,560 enhanced font "Helvetica,12"

set border lw 1.2
set tics out nomirror
set style line 81 lc rgb "#cfcfcf" lt 1 lw 1.1
set grid back xtics ytics ls 81
set key left top opaque box width 1 samplen 2.2 spacing 1.1
set xlabel "Replicas"
set ylabel "Time (ms)"
set xtics nomirror
set ytics nomirror
set xrange [3:14]
set logscale y 10
set yrange [5:*]
unset mytics
unset ytics
set ytics nomirror
set for [v in "5 10 20 50 100 200 500 1000 2000 5000 10000"] ytics add (sprintf("%g", real(v)) real(v))

set style line 1 lc rgb "#d95f02" lt 1 lw 2.4 pt 7 ps 1.25
set style line 2 lc rgb "#1b9e77" lt 1 lw 2.4 pt 5 ps 1.25
set style line 3 lc rgb "#7570b3" lt 1 lw 2.4 pt 9 ps 1.25
set style line 4 lc rgb "#e7298a" lt 1 lw 2.4 pt 11 ps 1.25
set style line 5 lc rgb "#66a61e" lt 1 lw 2.4 pt 13 ps 1.25
set style line 6 lc rgb "#e6ab02" lt 1 lw 2.4 pt 4 ps 1.25
set style line 7 lc rgb "#a6761d" lt 1 lw 2.4 pt 6 ps 1.25
set style line 8 lc rgb "#666666" lt 1 lw 2.4 pt 8 ps 1.25

set output sprintf("%s/search_latency_by_replicas_median_fresh.png", output_dir)
set title "Median Search Latency by Replicas (Fresh)"
plot for [i=1:bucket_count] input_path using ((strcol(7) eq "fresh" && strcol(3) eq word(bucket_specs, i)) ? $1 : 1/0):10 with linespoints ls i title word(bucket_labels, i)

set output sprintf("%s/search_latency_by_replicas_median_cached.png", output_dir)
set title "Median Search Latency by Replicas (Cached)"
plot for [i=1:bucket_count] input_path using ((strcol(7) eq "cached" && strcol(3) eq word(bucket_specs, i)) ? $1 : 1/0):10 with linespoints ls i title word(bucket_labels, i)

set output sprintf("%s/search_latency_by_replicas_mean_fresh.png", output_dir)
set title "Mean Search Latency by Replicas (Fresh)"
plot for [i=1:bucket_count] input_path using ((strcol(7) eq "fresh" && strcol(3) eq word(bucket_specs, i)) ? $1 : 1/0):11 with linespoints ls i title word(bucket_labels, i)

set output sprintf("%s/search_latency_by_replicas_mean_cached.png", output_dir)
set title "Mean Search Latency by Replicas (Cached)"
plot for [i=1:bucket_count] input_path using ((strcol(7) eq "cached" && strcol(3) eq word(bucket_specs, i)) ? $1 : 1/0):11 with linespoints ls i title word(bucket_labels, i)

unset output
