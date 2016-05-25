# CSVAnalyzer-jenkins
This plugn belongs to family of Jenkins plugins that are used during performance tests (load tests).<br>
Purpose of this plugn isn CSV files visualization.<br>
<br>
The plugin is able to analyze and visualize any CSV files with followng required columns:<br>
<b>Host</b> - hostname string<br>
<b>Process</b> - process name string<br>
<b>PID</b> - process ID<br>
<b>timestamp</b> - unix timestamp (seconds from 1970)<br>

Example:
<pre>
Host , Process,  PID,   Timestamp, custom_table1, custom_table2, ... 
host1,    java, 3004,  1461081950,       2075712,           1.0, ...
host1,    java, 14136, 1461081950,       2048928,           1.5, ...
host1,    java, 15924, 1461081950,       1082868,           1.2, ...
host1,    bash, 16960, 1461111121,         13420,           0.0, ...
</pre>



