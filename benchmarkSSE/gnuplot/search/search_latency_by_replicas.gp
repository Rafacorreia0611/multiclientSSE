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

if (!exists("plot_font")) {
    plot_font = "Helvetica"
}

bucket_count = words(bucket_specs)

set datafile separator "\t"
set terminal pngcairo size 980,560 enhanced font sprintf("%s,12", plot_font)

set border lw 1.2
set tics out nomirror
set style line 81 lc rgb "#cfcfcf" lt 1 lw 1.1
set grid back xtics ytics ls 81
set key left top opaque box width 1 samplen 2.2 spacing 1.1
set xlabel "Replicas"
set ylabel "Time (ms)"
set xtics ("4" 4, "7" 7, "10" 10, "13" 13) nomirror
set ytics nomirror
set xrange [4:13]
set logscale y 10
set yrange [5:*]
unset mytics
unset ytics
set ytics nomirror
set for [v in "5 10 20 50 100 200 500 1000 2000 5000 10000"] ytics add (sprintf("%g", real(v)) real(v))

set style line 1 lc rgb "#d95f02" lt 1 lw 1.45 pt 7 ps 1.45
set style line 2 lc rgb "#1b9e77" lt 1 lw 1.45 pt 5 ps 1.45
set style line 3 lc rgb "#7570b3" lt 1 lw 1.45 pt 9 ps 1.45
set style line 4 lc rgb "#e7298a" lt 1 lw 1.05 pt 11 ps 1.35
set style line 5 lc rgb "#66a61e" lt 1 lw 2.4 pt 13 ps 1.25
set style line 6 lc rgb "#e6ab02" lt 1 lw 2.4 pt 4 ps 1.25
set style line 7 lc rgb "#a6761d" lt 1 lw 2.4 pt 6 ps 1.25
set style line 8 lc rgb "#666666" lt 1 lw 2.4 pt 8 ps 1.25

set output sprintf("%s/search_latency_by_replicas_median_fresh.png", output_dir)
unset title
plot for [i=1:bucket_count] sprintf("< awk -F'\\t' 'NR > 1 && $7 == \"fresh\" && $3 == \"%s\" { print $1 \"\\t\" $10 }' %s", word(bucket_specs, i), input_path) using 1:2 with linespoints ls i title sprintf("%s Docs Returned", word(bucket_labels, i))

set output sprintf("%s/search_latency_by_replicas_median_cached.png", output_dir)
unset title
plot for [i=1:bucket_count] sprintf("< awk -F'\\t' 'NR > 1 && $7 == \"cached\" && $3 == \"%s\" { print $1 \"\\t\" $10 }' %s", word(bucket_specs, i), input_path) using 1:2 with linespoints ls i title sprintf("%s Docs Returned", word(bucket_labels, i))

set output sprintf("%s/search_latency_by_replicas_mean_fresh.png", output_dir)
unset title
plot for [i=1:bucket_count] sprintf("< awk -F'\\t' 'NR > 1 && $7 == \"fresh\" && $3 == \"%s\" { print $1 \"\\t\" $11 }' %s", word(bucket_specs, i), input_path) using 1:2 with linespoints ls i title sprintf("%s Docs Returned", word(bucket_labels, i))

set output sprintf("%s/search_latency_by_replicas_mean_cached.png", output_dir)
unset title
plot for [i=1:bucket_count] sprintf("< awk -F'\\t' 'NR > 1 && $7 == \"cached\" && $3 == \"%s\" { print $1 \"\\t\" $11 }' %s", word(bucket_specs, i), input_path) using 1:2 with linespoints ls i title sprintf("%s Docs Returned", word(bucket_labels, i))

unset output
