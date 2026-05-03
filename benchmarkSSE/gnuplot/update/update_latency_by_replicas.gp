if (!exists("input_path")) {
    input_path = "benchmarkSSE/results/data/update_latency_by_replicas_summary.tsv"
}

if (!exists("output_dir")) {
    output_dir = "benchmarkSSE/plots"
}

if (!exists("replica_values")) {
    replica_values = "4 7 10"
}

if (!exists("plot_font")) {
    plot_font = "Helvetica"
}

replica_count = words(replica_values)

set datafile separator "\t"
set terminal pngcairo size 980,560 enhanced font sprintf("%s,12", plot_font)

set border lw 1.2
set tics out nomirror
set style line 81 lc rgb "#cfcfcf" lt 1 lw 1.1
set grid back xtics ytics ls 81
set key left top opaque box width 1 samplen 2.2 spacing 1.1
set xlabel "Associations per update"
set ylabel "Update latency (ms)"
set logscale x 10
set logscale y 10
set xrange [0.8:*]
set yrange [1:*]
set format x "%g"
set format y "%g"

set style line 1 lc rgb "#d95f02" lt 1 lw 1.6 pt 7 ps 1.35
set style line 2 lc rgb "#1b9e77" lt 1 lw 1.6 pt 5 ps 1.35
set style line 3 lc rgb "#7570b3" lt 1 lw 1.6 pt 9 ps 1.35
set style line 4 lc rgb "#e7298a" lt 1 lw 1.25 pt 11 ps 1.25
set style line 5 lc rgb "#66a61e" lt 1 lw 1.25 pt 13 ps 1.20
set style line 6 lc rgb "#e6ab02" lt 1 lw 1.25 pt 4 ps 1.20
set style line 7 lc rgb "#a6761d" lt 1 lw 1.25 pt 6 ps 1.20
set style line 8 lc rgb "#666666" lt 1 lw 1.25 pt 8 ps 1.20

set output sprintf("%s/update_latency_by_replicas.png", output_dir)
unset title
plot for [i=1:replica_count] sprintf("< awk -F'\\t' 'NR > 1 && $1 == \"%s\" { print $4 \"\\t\" $9 }' %s | sort -n", word(replica_values, i), input_path) using 1:2 with linespoints ls i title sprintf("%s replicas", word(replica_values, i))

unset output
