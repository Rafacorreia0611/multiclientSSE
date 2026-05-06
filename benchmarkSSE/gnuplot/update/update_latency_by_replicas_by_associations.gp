if (!exists("input_path")) {
    input_path = "benchmarkSSE/results/final_results_update_latency/update_latency_by_replicas_summary.tsv"
}

if (!exists("output_dir")) {
    output_dir = "benchmarkSSE/results/final_results_update_latency/plots"
}

if (!exists("association_values")) {
    association_values = "1000 5000 10000 15000"
}

if (!exists("association_labels")) {
    association_labels = "1k 5k 10k 15k"
}

if (!exists("plot_font")) {
    plot_font = "Helvetica"
}

association_count = words(association_values)

set datafile separator "\t"
set terminal pngcairo size 980,560 enhanced font sprintf("%s,12", plot_font)

set border lw 1.2
set tics out nomirror
set style line 81 lc rgb "#cfcfcf" lt 1 lw 1.1
set grid back xtics ytics ls 81
set key left top opaque box width 0 samplen 2.1 spacing 1.08
set xlabel "Replicas"
set ylabel "Latency (ms)"
set xtics ("4" 4, "7" 7, "10" 10, "13" 13) nomirror
set ytics nomirror
set xrange [4:13]
set logscale y 10
set yrange [5000:200000]
unset mytics
unset ytics
set ytics nomirror
set for [v in "5000 7500 10000 15000 20000 35000 50000 75000 100000 150000 200000"] ytics add (sprintf("%g", real(v)) real(v))

set style line 1 lc rgb "#d95f02" lt 1 lw 1.7 pt 7 ps 1.55
set style line 2 lc rgb "#1b9e77" lt 1 lw 1.7 pt 5 ps 1.55
set style line 3 lc rgb "#7570b3" lt 1 lw 1.7 pt 9 ps 1.55
set style line 4 lc rgb "#e7298a" lt 1 lw 1.7 pt 11 ps 1.45
set style line 5 lc rgb "#66a61e" lt 1 lw 1.4 pt 13 ps 1.35
set style line 6 lc rgb "#e6ab02" lt 1 lw 1.4 pt 4 ps 1.35
set style line 7 lc rgb "#a6761d" lt 1 lw 1.4 pt 6 ps 1.35
set style line 8 lc rgb "#666666" lt 1 lw 1.4 pt 8 ps 1.35

set output sprintf("%s/update_latency_by_replicas.png", output_dir)
unset title
plot for [i=1:association_count] sprintf("< awk -F'\\t' 'NR > 1 && $4 == \"%s\" { print $1 \"\\t\" $9 }' %s | sort -n", word(association_values, i), input_path) using 1:2 with linespoints ls i title sprintf("%s Update Size", word(association_labels, i))

unset output
