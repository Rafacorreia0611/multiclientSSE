if (!exists("input_path")) {
    input_path = "benchmarkSSE/results/search_latency_by_docs_summary.tsv"
}

if (!exists("mean_output_path")) {
    mean_output_path = "benchmarkSSE/plots/search_latency_mean.png"
}

if (!exists("median_output_path")) {
    median_output_path = "benchmarkSSE/plots/search_latency_median.png"
}

set datafile separator "\t"
set terminal pngcairo size 900,540 enhanced font "Helvetica,12"

set border lw 1.2
set tics out nomirror
set style line 81 lc rgb "#cfcfcf" lt 1 lw 1.1
set grid back xtics ytics ls 81
set key left top opaque box width 1 samplen 2.2 spacing 1.1
set xlabel "Result Size Range"
set ylabel "Time (ms)"
set xrange [7:14000]
set yrange [5:2000]
set logscale x 10
set logscale y 10
set xtics rotate by 0 font "Helvetica,11"
set mxtics 10
unset mytics
unset ytics
set ytics nomirror
set for [v in "5 10 20 50 100 200 500 1000 2000"] ytics add (sprintf("%g", real(v)) real(v))

set style line 1 lc rgb "#d95f02" lt 1 lw 2.5 pt 7 ps 1.4
set style line 2 lc rgb "#1b9e77" lt 1 lw 2.5 pt 5 ps 1.4

set output mean_output_path
set title "Mean Search Latency"
plot input_path every 2::0 using (sqrt($3 * $4)):9:xticlabels(2) with linespoints ls 1 title "Fresh", \
     input_path every 2::1 using (sqrt($3 * $4)):9 with linespoints ls 2 title "Cached"

set output median_output_path
set title "Median Search Latency"
plot input_path every 2::0 using (sqrt($3 * $4)):8:xticlabels(2) with linespoints ls 1 title "Fresh", \
     input_path every 2::1 using (sqrt($3 * $4)):8 with linespoints ls 2 title "Cached"

unset output
