/*
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
$(document).ready(function() {

    $(".click-title").mouseenter( function(    e){
        e.preventDefault();
        this.style.cursor="pointer";
    });
    $(".click-title").mousedown( function(event){
        event.preventDefault();
    });

    // Ugly code while this script is shared among several pages
    try{
        refreshHitsPerSecond(true);
    } catch(e){}
    try{
        refreshResponseTimeOverTime(true);
    } catch(e){}
    try{
        refreshResponseTimePercentiles();
    } catch(e){}
});


var responseTimePercentilesInfos = {
        data: {"result": {"minY": 239.0, "minX": 0.0, "maxY": 1779.0, "series": [{"data": [[0.0, 239.0], [0.1, 239.0], [0.2, 239.0], [0.3, 239.0], [0.4, 239.0], [0.5, 239.0], [0.6, 239.0], [0.7, 239.0], [0.8, 239.0], [0.9, 239.0], [1.0, 243.0], [1.1, 243.0], [1.2, 243.0], [1.3, 243.0], [1.4, 243.0], [1.5, 243.0], [1.6, 243.0], [1.7, 243.0], [1.8, 243.0], [1.9, 243.0], [2.0, 244.0], [2.1, 244.0], [2.2, 244.0], [2.3, 244.0], [2.4, 244.0], [2.5, 244.0], [2.6, 244.0], [2.7, 244.0], [2.8, 244.0], [2.9, 244.0], [3.0, 244.0], [3.1, 244.0], [3.2, 244.0], [3.3, 244.0], [3.4, 244.0], [3.5, 244.0], [3.6, 244.0], [3.7, 244.0], [3.8, 244.0], [3.9, 244.0], [4.0, 245.0], [4.1, 245.0], [4.2, 245.0], [4.3, 245.0], [4.4, 245.0], [4.5, 245.0], [4.6, 245.0], [4.7, 245.0], [4.8, 245.0], [4.9, 245.0], [5.0, 247.0], [5.1, 247.0], [5.2, 247.0], [5.3, 247.0], [5.4, 247.0], [5.5, 247.0], [5.6, 247.0], [5.7, 247.0], [5.8, 247.0], [5.9, 247.0], [6.0, 250.0], [6.1, 250.0], [6.2, 250.0], [6.3, 250.0], [6.4, 250.0], [6.5, 250.0], [6.6, 250.0], [6.7, 250.0], [6.8, 250.0], [6.9, 250.0], [7.0, 252.0], [7.1, 252.0], [7.2, 252.0], [7.3, 252.0], [7.4, 252.0], [7.5, 252.0], [7.6, 252.0], [7.7, 252.0], [7.8, 252.0], [7.9, 252.0], [8.0, 254.0], [8.1, 254.0], [8.2, 254.0], [8.3, 254.0], [8.4, 254.0], [8.5, 254.0], [8.6, 254.0], [8.7, 254.0], [8.8, 254.0], [8.9, 254.0], [9.0, 258.0], [9.1, 258.0], [9.2, 258.0], [9.3, 258.0], [9.4, 258.0], [9.5, 258.0], [9.6, 258.0], [9.7, 258.0], [9.8, 258.0], [9.9, 258.0], [10.0, 258.0], [10.1, 258.0], [10.2, 258.0], [10.3, 258.0], [10.4, 258.0], [10.5, 258.0], [10.6, 258.0], [10.7, 258.0], [10.8, 258.0], [10.9, 258.0], [11.0, 258.0], [11.1, 258.0], [11.2, 258.0], [11.3, 258.0], [11.4, 258.0], [11.5, 258.0], [11.6, 258.0], [11.7, 258.0], [11.8, 258.0], [11.9, 258.0], [12.0, 260.0], [12.1, 260.0], [12.2, 260.0], [12.3, 260.0], [12.4, 260.0], [12.5, 260.0], [12.6, 260.0], [12.7, 260.0], [12.8, 260.0], [12.9, 260.0], [13.0, 260.0], [13.1, 260.0], [13.2, 260.0], [13.3, 260.0], [13.4, 260.0], [13.5, 260.0], [13.6, 260.0], [13.7, 260.0], [13.8, 260.0], [13.9, 260.0], [14.0, 263.0], [14.1, 263.0], [14.2, 263.0], [14.3, 263.0], [14.4, 263.0], [14.5, 263.0], [14.6, 263.0], [14.7, 263.0], [14.8, 263.0], [14.9, 263.0], [15.0, 263.0], [15.1, 263.0], [15.2, 263.0], [15.3, 263.0], [15.4, 263.0], [15.5, 263.0], [15.6, 263.0], [15.7, 263.0], [15.8, 263.0], [15.9, 263.0], [16.0, 263.0], [16.1, 263.0], [16.2, 263.0], [16.3, 263.0], [16.4, 263.0], [16.5, 263.0], [16.6, 263.0], [16.7, 263.0], [16.8, 263.0], [16.9, 263.0], [17.0, 263.0], [17.1, 263.0], [17.2, 263.0], [17.3, 263.0], [17.4, 263.0], [17.5, 263.0], [17.6, 263.0], [17.7, 263.0], [17.8, 263.0], [17.9, 263.0], [18.0, 265.0], [18.1, 265.0], [18.2, 265.0], [18.3, 265.0], [18.4, 265.0], [18.5, 265.0], [18.6, 265.0], [18.7, 265.0], [18.8, 265.0], [18.9, 265.0], [19.0, 266.0], [19.1, 266.0], [19.2, 266.0], [19.3, 266.0], [19.4, 266.0], [19.5, 266.0], [19.6, 266.0], [19.7, 266.0], [19.8, 266.0], [19.9, 266.0], [20.0, 268.0], [20.1, 268.0], [20.2, 268.0], [20.3, 268.0], [20.4, 268.0], [20.5, 268.0], [20.6, 268.0], [20.7, 268.0], [20.8, 268.0], [20.9, 268.0], [21.0, 269.0], [21.1, 269.0], [21.2, 269.0], [21.3, 269.0], [21.4, 269.0], [21.5, 269.0], [21.6, 269.0], [21.7, 269.0], [21.8, 269.0], [21.9, 269.0], [22.0, 270.0], [22.1, 270.0], [22.2, 270.0], [22.3, 270.0], [22.4, 270.0], [22.5, 270.0], [22.6, 270.0], [22.7, 270.0], [22.8, 270.0], [22.9, 270.0], [23.0, 271.0], [23.1, 271.0], [23.2, 271.0], [23.3, 271.0], [23.4, 271.0], [23.5, 271.0], [23.6, 271.0], [23.7, 271.0], [23.8, 271.0], [23.9, 271.0], [24.0, 273.0], [24.1, 273.0], [24.2, 273.0], [24.3, 273.0], [24.4, 273.0], [24.5, 273.0], [24.6, 273.0], [24.7, 273.0], [24.8, 273.0], [24.9, 273.0], [25.0, 273.0], [25.1, 273.0], [25.2, 273.0], [25.3, 273.0], [25.4, 273.0], [25.5, 273.0], [25.6, 273.0], [25.7, 273.0], [25.8, 273.0], [25.9, 273.0], [26.0, 273.0], [26.1, 273.0], [26.2, 273.0], [26.3, 273.0], [26.4, 273.0], [26.5, 273.0], [26.6, 273.0], [26.7, 273.0], [26.8, 273.0], [26.9, 273.0], [27.0, 273.0], [27.1, 273.0], [27.2, 273.0], [27.3, 273.0], [27.4, 273.0], [27.5, 273.0], [27.6, 273.0], [27.7, 273.0], [27.8, 273.0], [27.9, 273.0], [28.0, 274.0], [28.1, 274.0], [28.2, 274.0], [28.3, 274.0], [28.4, 274.0], [28.5, 274.0], [28.6, 274.0], [28.7, 274.0], [28.8, 274.0], [28.9, 274.0], [29.0, 274.0], [29.1, 274.0], [29.2, 274.0], [29.3, 274.0], [29.4, 274.0], [29.5, 274.0], [29.6, 274.0], [29.7, 274.0], [29.8, 274.0], [29.9, 274.0], [30.0, 274.0], [30.1, 274.0], [30.2, 274.0], [30.3, 274.0], [30.4, 274.0], [30.5, 274.0], [30.6, 274.0], [30.7, 274.0], [30.8, 274.0], [30.9, 274.0], [31.0, 275.0], [31.1, 275.0], [31.2, 275.0], [31.3, 275.0], [31.4, 275.0], [31.5, 275.0], [31.6, 275.0], [31.7, 275.0], [31.8, 275.0], [31.9, 275.0], [32.0, 275.0], [32.1, 275.0], [32.2, 275.0], [32.3, 275.0], [32.4, 275.0], [32.5, 275.0], [32.6, 275.0], [32.7, 275.0], [32.8, 275.0], [32.9, 275.0], [33.0, 275.0], [33.1, 275.0], [33.2, 275.0], [33.3, 275.0], [33.4, 275.0], [33.5, 275.0], [33.6, 275.0], [33.7, 275.0], [33.8, 275.0], [33.9, 275.0], [34.0, 275.0], [34.1, 275.0], [34.2, 275.0], [34.3, 275.0], [34.4, 275.0], [34.5, 275.0], [34.6, 275.0], [34.7, 275.0], [34.8, 275.0], [34.9, 275.0], [35.0, 276.0], [35.1, 276.0], [35.2, 276.0], [35.3, 276.0], [35.4, 276.0], [35.5, 276.0], [35.6, 276.0], [35.7, 276.0], [35.8, 276.0], [35.9, 276.0], [36.0, 279.0], [36.1, 279.0], [36.2, 279.0], [36.3, 279.0], [36.4, 279.0], [36.5, 279.0], [36.6, 279.0], [36.7, 279.0], [36.8, 279.0], [36.9, 279.0], [37.0, 281.0], [37.1, 281.0], [37.2, 281.0], [37.3, 281.0], [37.4, 281.0], [37.5, 281.0], [37.6, 281.0], [37.7, 281.0], [37.8, 281.0], [37.9, 281.0], [38.0, 282.0], [38.1, 282.0], [38.2, 282.0], [38.3, 282.0], [38.4, 282.0], [38.5, 282.0], [38.6, 282.0], [38.7, 282.0], [38.8, 282.0], [38.9, 282.0], [39.0, 282.0], [39.1, 282.0], [39.2, 282.0], [39.3, 282.0], [39.4, 282.0], [39.5, 282.0], [39.6, 282.0], [39.7, 282.0], [39.8, 282.0], [39.9, 282.0], [40.0, 284.0], [40.1, 284.0], [40.2, 284.0], [40.3, 284.0], [40.4, 284.0], [40.5, 284.0], [40.6, 284.0], [40.7, 284.0], [40.8, 284.0], [40.9, 284.0], [41.0, 284.0], [41.1, 284.0], [41.2, 284.0], [41.3, 284.0], [41.4, 284.0], [41.5, 284.0], [41.6, 284.0], [41.7, 284.0], [41.8, 284.0], [41.9, 284.0], [42.0, 287.0], [42.1, 287.0], [42.2, 287.0], [42.3, 287.0], [42.4, 287.0], [42.5, 287.0], [42.6, 287.0], [42.7, 287.0], [42.8, 287.0], [42.9, 287.0], [43.0, 288.0], [43.1, 288.0], [43.2, 288.0], [43.3, 288.0], [43.4, 288.0], [43.5, 288.0], [43.6, 288.0], [43.7, 288.0], [43.8, 288.0], [43.9, 288.0], [44.0, 289.0], [44.1, 289.0], [44.2, 289.0], [44.3, 289.0], [44.4, 289.0], [44.5, 289.0], [44.6, 289.0], [44.7, 289.0], [44.8, 289.0], [44.9, 289.0], [45.0, 291.0], [45.1, 291.0], [45.2, 291.0], [45.3, 291.0], [45.4, 291.0], [45.5, 291.0], [45.6, 291.0], [45.7, 291.0], [45.8, 291.0], [45.9, 291.0], [46.0, 291.0], [46.1, 291.0], [46.2, 291.0], [46.3, 291.0], [46.4, 291.0], [46.5, 291.0], [46.6, 291.0], [46.7, 291.0], [46.8, 291.0], [46.9, 291.0], [47.0, 292.0], [47.1, 292.0], [47.2, 292.0], [47.3, 292.0], [47.4, 292.0], [47.5, 292.0], [47.6, 292.0], [47.7, 292.0], [47.8, 292.0], [47.9, 292.0], [48.0, 293.0], [48.1, 293.0], [48.2, 293.0], [48.3, 293.0], [48.4, 293.0], [48.5, 293.0], [48.6, 293.0], [48.7, 293.0], [48.8, 293.0], [48.9, 293.0], [49.0, 294.0], [49.1, 294.0], [49.2, 294.0], [49.3, 294.0], [49.4, 294.0], [49.5, 294.0], [49.6, 294.0], [49.7, 294.0], [49.8, 294.0], [49.9, 294.0], [50.0, 294.0], [50.1, 294.0], [50.2, 294.0], [50.3, 294.0], [50.4, 294.0], [50.5, 294.0], [50.6, 294.0], [50.7, 294.0], [50.8, 294.0], [50.9, 294.0], [51.0, 294.0], [51.1, 294.0], [51.2, 294.0], [51.3, 294.0], [51.4, 294.0], [51.5, 294.0], [51.6, 294.0], [51.7, 294.0], [51.8, 294.0], [51.9, 294.0], [52.0, 299.0], [52.1, 299.0], [52.2, 299.0], [52.3, 299.0], [52.4, 299.0], [52.5, 299.0], [52.6, 299.0], [52.7, 299.0], [52.8, 299.0], [52.9, 299.0], [53.0, 308.0], [53.1, 308.0], [53.2, 308.0], [53.3, 308.0], [53.4, 308.0], [53.5, 308.0], [53.6, 308.0], [53.7, 308.0], [53.8, 308.0], [53.9, 308.0], [54.0, 311.0], [54.1, 311.0], [54.2, 311.0], [54.3, 311.0], [54.4, 311.0], [54.5, 311.0], [54.6, 311.0], [54.7, 311.0], [54.8, 311.0], [54.9, 311.0], [55.0, 311.0], [55.1, 311.0], [55.2, 311.0], [55.3, 311.0], [55.4, 311.0], [55.5, 311.0], [55.6, 311.0], [55.7, 311.0], [55.8, 311.0], [55.9, 311.0], [56.0, 312.0], [56.1, 312.0], [56.2, 312.0], [56.3, 312.0], [56.4, 312.0], [56.5, 312.0], [56.6, 312.0], [56.7, 312.0], [56.8, 312.0], [56.9, 312.0], [57.0, 312.0], [57.1, 312.0], [57.2, 312.0], [57.3, 312.0], [57.4, 312.0], [57.5, 312.0], [57.6, 312.0], [57.7, 312.0], [57.8, 312.0], [57.9, 312.0], [58.0, 313.0], [58.1, 313.0], [58.2, 313.0], [58.3, 313.0], [58.4, 313.0], [58.5, 313.0], [58.6, 313.0], [58.7, 313.0], [58.8, 313.0], [58.9, 313.0], [59.0, 315.0], [59.1, 315.0], [59.2, 315.0], [59.3, 315.0], [59.4, 315.0], [59.5, 315.0], [59.6, 315.0], [59.7, 315.0], [59.8, 315.0], [59.9, 315.0], [60.0, 316.0], [60.1, 316.0], [60.2, 316.0], [60.3, 316.0], [60.4, 316.0], [60.5, 316.0], [60.6, 316.0], [60.7, 316.0], [60.8, 316.0], [60.9, 316.0], [61.0, 316.0], [61.1, 316.0], [61.2, 316.0], [61.3, 316.0], [61.4, 316.0], [61.5, 316.0], [61.6, 316.0], [61.7, 316.0], [61.8, 316.0], [61.9, 316.0], [62.0, 321.0], [62.1, 321.0], [62.2, 321.0], [62.3, 321.0], [62.4, 321.0], [62.5, 321.0], [62.6, 321.0], [62.7, 321.0], [62.8, 321.0], [62.9, 321.0], [63.0, 322.0], [63.1, 322.0], [63.2, 322.0], [63.3, 322.0], [63.4, 322.0], [63.5, 322.0], [63.6, 322.0], [63.7, 322.0], [63.8, 322.0], [63.9, 322.0], [64.0, 324.0], [64.1, 324.0], [64.2, 324.0], [64.3, 324.0], [64.4, 324.0], [64.5, 324.0], [64.6, 324.0], [64.7, 324.0], [64.8, 324.0], [64.9, 324.0], [65.0, 324.0], [65.1, 324.0], [65.2, 324.0], [65.3, 324.0], [65.4, 324.0], [65.5, 324.0], [65.6, 324.0], [65.7, 324.0], [65.8, 324.0], [65.9, 324.0], [66.0, 324.0], [66.1, 324.0], [66.2, 324.0], [66.3, 324.0], [66.4, 324.0], [66.5, 324.0], [66.6, 324.0], [66.7, 324.0], [66.8, 324.0], [66.9, 324.0], [67.0, 324.0], [67.1, 324.0], [67.2, 324.0], [67.3, 324.0], [67.4, 324.0], [67.5, 324.0], [67.6, 324.0], [67.7, 324.0], [67.8, 324.0], [67.9, 324.0], [68.0, 325.0], [68.1, 325.0], [68.2, 325.0], [68.3, 325.0], [68.4, 325.0], [68.5, 325.0], [68.6, 325.0], [68.7, 325.0], [68.8, 325.0], [68.9, 325.0], [69.0, 326.0], [69.1, 326.0], [69.2, 326.0], [69.3, 326.0], [69.4, 326.0], [69.5, 326.0], [69.6, 326.0], [69.7, 326.0], [69.8, 326.0], [69.9, 326.0], [70.0, 327.0], [70.1, 327.0], [70.2, 327.0], [70.3, 327.0], [70.4, 327.0], [70.5, 327.0], [70.6, 327.0], [70.7, 327.0], [70.8, 327.0], [70.9, 327.0], [71.0, 328.0], [71.1, 328.0], [71.2, 328.0], [71.3, 328.0], [71.4, 328.0], [71.5, 328.0], [71.6, 328.0], [71.7, 328.0], [71.8, 328.0], [71.9, 328.0], [72.0, 330.0], [72.1, 330.0], [72.2, 330.0], [72.3, 330.0], [72.4, 330.0], [72.5, 330.0], [72.6, 330.0], [72.7, 330.0], [72.8, 330.0], [72.9, 330.0], [73.0, 331.0], [73.1, 331.0], [73.2, 331.0], [73.3, 331.0], [73.4, 331.0], [73.5, 331.0], [73.6, 331.0], [73.7, 331.0], [73.8, 331.0], [73.9, 331.0], [74.0, 333.0], [74.1, 333.0], [74.2, 333.0], [74.3, 333.0], [74.4, 333.0], [74.5, 333.0], [74.6, 333.0], [74.7, 333.0], [74.8, 333.0], [74.9, 333.0], [75.0, 333.0], [75.1, 333.0], [75.2, 333.0], [75.3, 333.0], [75.4, 333.0], [75.5, 333.0], [75.6, 333.0], [75.7, 333.0], [75.8, 333.0], [75.9, 333.0], [76.0, 335.0], [76.1, 335.0], [76.2, 335.0], [76.3, 335.0], [76.4, 335.0], [76.5, 335.0], [76.6, 335.0], [76.7, 335.0], [76.8, 335.0], [76.9, 335.0], [77.0, 336.0], [77.1, 336.0], [77.2, 336.0], [77.3, 336.0], [77.4, 336.0], [77.5, 336.0], [77.6, 336.0], [77.7, 336.0], [77.8, 336.0], [77.9, 336.0], [78.0, 337.0], [78.1, 337.0], [78.2, 337.0], [78.3, 337.0], [78.4, 337.0], [78.5, 337.0], [78.6, 337.0], [78.7, 337.0], [78.8, 337.0], [78.9, 337.0], [79.0, 345.0], [79.1, 345.0], [79.2, 345.0], [79.3, 345.0], [79.4, 345.0], [79.5, 345.0], [79.6, 345.0], [79.7, 345.0], [79.8, 345.0], [79.9, 345.0], [80.0, 348.0], [80.1, 348.0], [80.2, 348.0], [80.3, 348.0], [80.4, 348.0], [80.5, 348.0], [80.6, 348.0], [80.7, 348.0], [80.8, 348.0], [80.9, 348.0], [81.0, 351.0], [81.1, 351.0], [81.2, 351.0], [81.3, 351.0], [81.4, 351.0], [81.5, 351.0], [81.6, 351.0], [81.7, 351.0], [81.8, 351.0], [81.9, 351.0], [82.0, 359.0], [82.1, 359.0], [82.2, 359.0], [82.3, 359.0], [82.4, 359.0], [82.5, 359.0], [82.6, 359.0], [82.7, 359.0], [82.8, 359.0], [82.9, 359.0], [83.0, 373.0], [83.1, 373.0], [83.2, 373.0], [83.3, 373.0], [83.4, 373.0], [83.5, 373.0], [83.6, 373.0], [83.7, 373.0], [83.8, 373.0], [83.9, 373.0], [84.0, 403.0], [84.1, 403.0], [84.2, 403.0], [84.3, 403.0], [84.4, 403.0], [84.5, 403.0], [84.6, 403.0], [84.7, 403.0], [84.8, 403.0], [84.9, 403.0], [85.0, 454.0], [85.1, 454.0], [85.2, 454.0], [85.3, 454.0], [85.4, 454.0], [85.5, 454.0], [85.6, 454.0], [85.7, 454.0], [85.8, 454.0], [85.9, 454.0], [86.0, 465.0], [86.1, 465.0], [86.2, 465.0], [86.3, 465.0], [86.4, 465.0], [86.5, 465.0], [86.6, 465.0], [86.7, 465.0], [86.8, 465.0], [86.9, 465.0], [87.0, 484.0], [87.1, 484.0], [87.2, 484.0], [87.3, 484.0], [87.4, 484.0], [87.5, 484.0], [87.6, 484.0], [87.7, 484.0], [87.8, 484.0], [87.9, 484.0], [88.0, 491.0], [88.1, 491.0], [88.2, 491.0], [88.3, 491.0], [88.4, 491.0], [88.5, 491.0], [88.6, 491.0], [88.7, 491.0], [88.8, 491.0], [88.9, 491.0], [89.0, 518.0], [89.1, 518.0], [89.2, 518.0], [89.3, 518.0], [89.4, 518.0], [89.5, 518.0], [89.6, 518.0], [89.7, 518.0], [89.8, 518.0], [89.9, 518.0], [90.0, 608.0], [90.1, 608.0], [90.2, 608.0], [90.3, 608.0], [90.4, 608.0], [90.5, 608.0], [90.6, 608.0], [90.7, 608.0], [90.8, 608.0], [90.9, 608.0], [91.0, 618.0], [91.1, 618.0], [91.2, 618.0], [91.3, 618.0], [91.4, 618.0], [91.5, 618.0], [91.6, 618.0], [91.7, 618.0], [91.8, 618.0], [91.9, 618.0], [92.0, 631.0], [92.1, 631.0], [92.2, 631.0], [92.3, 631.0], [92.4, 631.0], [92.5, 631.0], [92.6, 631.0], [92.7, 631.0], [92.8, 631.0], [92.9, 631.0], [93.0, 757.0], [93.1, 757.0], [93.2, 757.0], [93.3, 757.0], [93.4, 757.0], [93.5, 757.0], [93.6, 757.0], [93.7, 757.0], [93.8, 757.0], [93.9, 757.0], [94.0, 816.0], [94.1, 816.0], [94.2, 816.0], [94.3, 816.0], [94.4, 816.0], [94.5, 816.0], [94.6, 816.0], [94.7, 816.0], [94.8, 816.0], [94.9, 816.0], [95.0, 877.0], [95.1, 877.0], [95.2, 877.0], [95.3, 877.0], [95.4, 877.0], [95.5, 877.0], [95.6, 877.0], [95.7, 877.0], [95.8, 877.0], [95.9, 877.0], [96.0, 997.0], [96.1, 997.0], [96.2, 997.0], [96.3, 997.0], [96.4, 997.0], [96.5, 997.0], [96.6, 997.0], [96.7, 997.0], [96.8, 997.0], [96.9, 997.0], [97.0, 1207.0], [97.1, 1207.0], [97.2, 1207.0], [97.3, 1207.0], [97.4, 1207.0], [97.5, 1207.0], [97.6, 1207.0], [97.7, 1207.0], [97.8, 1207.0], [97.9, 1207.0], [98.0, 1269.0], [98.1, 1269.0], [98.2, 1269.0], [98.3, 1269.0], [98.4, 1269.0], [98.5, 1269.0], [98.6, 1269.0], [98.7, 1269.0], [98.8, 1269.0], [98.9, 1269.0], [99.0, 1779.0], [99.1, 1779.0], [99.2, 1779.0], [99.3, 1779.0], [99.4, 1779.0], [99.5, 1779.0], [99.6, 1779.0], [99.7, 1779.0], [99.8, 1779.0], [99.9, 1779.0]], "isOverall": false, "label": "GET Target URL", "isController": false}], "supportsControllersDiscrimination": true, "maxX": 100.0, "title": "Response Time Percentiles"}},
        getOptions: function() {
            return {
                series: {
                    points: { show: false }
                },
                legend: {
                    noColumns: 2,
                    show: true,
                    container: '#legendResponseTimePercentiles'
                },
                xaxis: {
                    tickDecimals: 1,
                    axisLabel: "Percentiles",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Percentile value in ms",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: "%s : %x.2 percentile was %y ms"
                },
                selection: { mode: "xy" },
            };
        },
        createGraph: function() {
            var data = this.data;
            var dataset = prepareData(data.result.series, $("#choicesResponseTimePercentiles"));
            var options = this.getOptions();
            prepareOptions(options, data);
            $.plot($("#flotResponseTimesPercentiles"), dataset, options);
            // setup overview
            $.plot($("#overviewResponseTimesPercentiles"), dataset, prepareOverviewOptions(options));
        }
};

/**
 * @param elementId Id of element where we display message
 */
function setEmptyGraph(elementId) {
    $(function() {
        $(elementId).text("No graph series with filter="+seriesFilter);
    });
}

// Response times percentiles
function refreshResponseTimePercentiles() {
    var infos = responseTimePercentilesInfos;
    prepareSeries(infos.data);
    if(infos.data.result.series.length == 0) {
        setEmptyGraph("#bodyResponseTimePercentiles");
        return;
    }
    if (isGraph($("#flotResponseTimesPercentiles"))){
        infos.createGraph();
    } else {
        var choiceContainer = $("#choicesResponseTimePercentiles");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotResponseTimesPercentiles", "#overviewResponseTimesPercentiles");
        $('#bodyResponseTimePercentiles .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
}

var responseTimeDistributionInfos = {
        data: {"result": {"minY": 1.0, "minX": 200.0, "maxY": 53.0, "series": [{"data": [[300.0, 31.0], [600.0, 3.0], [1200.0, 2.0], [700.0, 1.0], [200.0, 53.0], [400.0, 5.0], [800.0, 2.0], [1700.0, 1.0], [900.0, 1.0], [500.0, 1.0]], "isOverall": false, "label": "GET Target URL", "isController": false}], "supportsControllersDiscrimination": true, "granularity": 100, "maxX": 1700.0, "title": "Response Time Distribution"}},
        getOptions: function() {
            var granularity = this.data.result.granularity;
            return {
                legend: {
                    noColumns: 2,
                    show: true,
                    container: '#legendResponseTimeDistribution'
                },
                xaxis:{
                    axisLabel: "Response times in ms",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Number of responses",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                bars : {
                    show: true,
                    barWidth: this.data.result.granularity
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: function(label, xval, yval, flotItem){
                        return yval + " responses for " + label + " were between " + xval + " and " + (xval + granularity) + " ms";
                    }
                }
            };
        },
        createGraph: function() {
            var data = this.data;
            var options = this.getOptions();
            prepareOptions(options, data);
            $.plot($("#flotResponseTimeDistribution"), prepareData(data.result.series, $("#choicesResponseTimeDistribution")), options);
        }

};

// Response time distribution
function refreshResponseTimeDistribution() {
    var infos = responseTimeDistributionInfos;
    prepareSeries(infos.data);
    if(infos.data.result.series.length == 0) {
        setEmptyGraph("#bodyResponseTimeDistribution");
        return;
    }
    if (isGraph($("#flotResponseTimeDistribution"))){
        infos.createGraph();
    }else{
        var choiceContainer = $("#choicesResponseTimeDistribution");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        $('#footerResponseTimeDistribution .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};


var syntheticResponseTimeDistributionInfos = {
        data: {"result": {"minY": 1.0, "minX": 0.0, "ticks": [[0, "Requests having \nresponse time <= 500ms"], [1, "Requests having \nresponse time > 500ms and <= 1,500ms"], [2, "Requests having \nresponse time > 1,500ms"], [3, "Requests in error"]], "maxY": 89.0, "series": [{"data": [[0.0, 89.0]], "color": "#9ACD32", "isOverall": false, "label": "Requests having \nresponse time <= 500ms", "isController": false}, {"data": [[1.0, 10.0]], "color": "yellow", "isOverall": false, "label": "Requests having \nresponse time > 500ms and <= 1,500ms", "isController": false}, {"data": [[2.0, 1.0]], "color": "orange", "isOverall": false, "label": "Requests having \nresponse time > 1,500ms", "isController": false}, {"data": [], "color": "#FF6347", "isOverall": false, "label": "Requests in error", "isController": false}], "supportsControllersDiscrimination": false, "maxX": 2.0, "title": "Synthetic Response Times Distribution"}},
        getOptions: function() {
            return {
                legend: {
                    noColumns: 2,
                    show: true,
                    container: '#legendSyntheticResponseTimeDistribution'
                },
                xaxis:{
                    axisLabel: "Response times ranges",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                    tickLength:0,
                    min:-0.5,
                    max:3.5
                },
                yaxis: {
                    axisLabel: "Number of responses",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                bars : {
                    show: true,
                    align: "center",
                    barWidth: 0.25,
                    fill:.75
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: function(label, xval, yval, flotItem){
                        return yval + " " + label;
                    }
                }
            };
        },
        createGraph: function() {
            var data = this.data;
            var options = this.getOptions();
            prepareOptions(options, data);
            options.xaxis.ticks = data.result.ticks;
            $.plot($("#flotSyntheticResponseTimeDistribution"), prepareData(data.result.series, $("#choicesSyntheticResponseTimeDistribution")), options);
        }

};

// Response time distribution
function refreshSyntheticResponseTimeDistribution() {
    var infos = syntheticResponseTimeDistributionInfos;
    prepareSeries(infos.data, true);
    if (isGraph($("#flotSyntheticResponseTimeDistribution"))){
        infos.createGraph();
    }else{
        var choiceContainer = $("#choicesSyntheticResponseTimeDistribution");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        $('#footerSyntheticResponseTimeDistribution .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};

var activeThreadsOverTimeInfos = {
        data: {"result": {"minY": 4.100000000000002, "minX": 1.78160766E12, "maxY": 4.100000000000002, "series": [{"data": [[1.78160766E12, 4.100000000000002]], "isOverall": false, "label": "Dashboard Virtual Users", "isController": false}], "supportsControllersDiscrimination": false, "granularity": 60000, "maxX": 1.78160766E12, "title": "Active Threads Over Time"}},
        getOptions: function() {
            return {
                series: {
                    stack: true,
                    lines: {
                        show: true,
                        fill: true
                    },
                    points: {
                        show: true
                    }
                },
                xaxis: {
                    mode: "time",
                    timeformat: getTimeFormat(this.data.result.granularity),
                    axisLabel: getElapsedTimeLabel(this.data.result.granularity),
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Number of active threads",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20
                },
                legend: {
                    noColumns: 6,
                    show: true,
                    container: '#legendActiveThreadsOverTime'
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                selection: {
                    mode: 'xy'
                },
                tooltip: true,
                tooltipOpts: {
                    content: "%s : At %x there were %y active threads"
                }
            };
        },
        createGraph: function() {
            var data = this.data;
            var dataset = prepareData(data.result.series, $("#choicesActiveThreadsOverTime"));
            var options = this.getOptions();
            prepareOptions(options, data);
            $.plot($("#flotActiveThreadsOverTime"), dataset, options);
            // setup overview
            $.plot($("#overviewActiveThreadsOverTime"), dataset, prepareOverviewOptions(options));
        }
};

// Active Threads Over Time
function refreshActiveThreadsOverTime(fixTimestamps) {
    var infos = activeThreadsOverTimeInfos;
    prepareSeries(infos.data);
    if(fixTimestamps) {
        fixTimeStamps(infos.data.result.series, 19800000);
    }
    if(isGraph($("#flotActiveThreadsOverTime"))) {
        infos.createGraph();
    }else{
        var choiceContainer = $("#choicesActiveThreadsOverTime");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotActiveThreadsOverTime", "#overviewActiveThreadsOverTime");
        $('#footerActiveThreadsOverTime .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};

var timeVsThreadsInfos = {
        data: {"result": {"minY": 276.3666666666667, "minX": 1.0, "maxY": 757.0, "series": [{"data": [[4.0, 353.51515151515156], [2.0, 327.0], [1.0, 757.0], [5.0, 403.49999999999994], [6.0, 596.375], [3.0, 276.3666666666667], [7.0, 608.0]], "isOverall": false, "label": "GET Target URL", "isController": false}, {"data": [[4.100000000000002, 369.1099999999999]], "isOverall": false, "label": "GET Target URL-Aggregated", "isController": false}], "supportsControllersDiscrimination": true, "maxX": 7.0, "title": "Time VS Threads"}},
        getOptions: function() {
            return {
                series: {
                    lines: {
                        show: true
                    },
                    points: {
                        show: true
                    }
                },
                xaxis: {
                    axisLabel: "Number of active threads",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Average response times in ms",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20
                },
                legend: { noColumns: 2,show: true, container: '#legendTimeVsThreads' },
                selection: {
                    mode: 'xy'
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to work
                },
                tooltip: true,
                tooltipOpts: {
                    content: "%s: At %x.2 active threads, Average response time was %y.2 ms"
                }
            };
        },
        createGraph: function() {
            var data = this.data;
            var dataset = prepareData(data.result.series, $("#choicesTimeVsThreads"));
            var options = this.getOptions();
            prepareOptions(options, data);
            $.plot($("#flotTimesVsThreads"), dataset, options);
            // setup overview
            $.plot($("#overviewTimesVsThreads"), dataset, prepareOverviewOptions(options));
        }
};

// Time vs threads
function refreshTimeVsThreads(){
    var infos = timeVsThreadsInfos;
    prepareSeries(infos.data);
    if(infos.data.result.series.length == 0) {
        setEmptyGraph("#bodyTimeVsThreads");
        return;
    }
    if(isGraph($("#flotTimesVsThreads"))){
        infos.createGraph();
    }else{
        var choiceContainer = $("#choicesTimeVsThreads");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotTimesVsThreads", "#overviewTimesVsThreads");
        $('#footerTimeVsThreads .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};

var bytesThroughputOverTimeInfos = {
        data : {"result": {"minY": 423.3333333333333, "minX": 1.78160766E12, "maxY": 133816.66666666666, "series": [{"data": [[1.78160766E12, 133816.66666666666]], "isOverall": false, "label": "Bytes received per second", "isController": false}, {"data": [[1.78160766E12, 423.3333333333333]], "isOverall": false, "label": "Bytes sent per second", "isController": false}], "supportsControllersDiscrimination": false, "granularity": 60000, "maxX": 1.78160766E12, "title": "Bytes Throughput Over Time"}},
        getOptions : function(){
            return {
                series: {
                    lines: {
                        show: true
                    },
                    points: {
                        show: true
                    }
                },
                xaxis: {
                    mode: "time",
                    timeformat: getTimeFormat(this.data.result.granularity),
                    axisLabel: getElapsedTimeLabel(this.data.result.granularity) ,
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Bytes / sec",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                legend: {
                    noColumns: 2,
                    show: true,
                    container: '#legendBytesThroughputOverTime'
                },
                selection: {
                    mode: "xy"
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: "%s at %x was %y"
                }
            };
        },
        createGraph : function() {
            var data = this.data;
            var dataset = prepareData(data.result.series, $("#choicesBytesThroughputOverTime"));
            var options = this.getOptions();
            prepareOptions(options, data);
            $.plot($("#flotBytesThroughputOverTime"), dataset, options);
            // setup overview
            $.plot($("#overviewBytesThroughputOverTime"), dataset, prepareOverviewOptions(options));
        }
};

// Bytes throughput Over Time
function refreshBytesThroughputOverTime(fixTimestamps) {
    var infos = bytesThroughputOverTimeInfos;
    prepareSeries(infos.data);
    if(fixTimestamps) {
        fixTimeStamps(infos.data.result.series, 19800000);
    }
    if(isGraph($("#flotBytesThroughputOverTime"))){
        infos.createGraph();
    }else{
        var choiceContainer = $("#choicesBytesThroughputOverTime");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotBytesThroughputOverTime", "#overviewBytesThroughputOverTime");
        $('#footerBytesThroughputOverTime .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
}

var responseTimesOverTimeInfos = {
        data: {"result": {"minY": 369.1099999999999, "minX": 1.78160766E12, "maxY": 369.1099999999999, "series": [{"data": [[1.78160766E12, 369.1099999999999]], "isOverall": false, "label": "GET Target URL", "isController": false}], "supportsControllersDiscrimination": true, "granularity": 60000, "maxX": 1.78160766E12, "title": "Response Time Over Time"}},
        getOptions: function(){
            return {
                series: {
                    lines: {
                        show: true
                    },
                    points: {
                        show: true
                    }
                },
                xaxis: {
                    mode: "time",
                    timeformat: getTimeFormat(this.data.result.granularity),
                    axisLabel: getElapsedTimeLabel(this.data.result.granularity),
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Average response time in ms",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                legend: {
                    noColumns: 2,
                    show: true,
                    container: '#legendResponseTimesOverTime'
                },
                selection: {
                    mode: 'xy'
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: "%s : at %x Average response time was %y ms"
                }
            };
        },
        createGraph: function() {
            var data = this.data;
            var dataset = prepareData(data.result.series, $("#choicesResponseTimesOverTime"));
            var options = this.getOptions();
            prepareOptions(options, data);
            $.plot($("#flotResponseTimesOverTime"), dataset, options);
            // setup overview
            $.plot($("#overviewResponseTimesOverTime"), dataset, prepareOverviewOptions(options));
        }
};

// Response Times Over Time
function refreshResponseTimeOverTime(fixTimestamps) {
    var infos = responseTimesOverTimeInfos;
    prepareSeries(infos.data);
    if(infos.data.result.series.length == 0) {
        setEmptyGraph("#bodyResponseTimeOverTime");
        return;
    }
    if(fixTimestamps) {
        fixTimeStamps(infos.data.result.series, 19800000);
    }
    if(isGraph($("#flotResponseTimesOverTime"))){
        infos.createGraph();
    }else{
        var choiceContainer = $("#choicesResponseTimesOverTime");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotResponseTimesOverTime", "#overviewResponseTimesOverTime");
        $('#footerResponseTimesOverTime .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};

var latenciesOverTimeInfos = {
        data: {"result": {"minY": 170.10000000000005, "minX": 1.78160766E12, "maxY": 170.10000000000005, "series": [{"data": [[1.78160766E12, 170.10000000000005]], "isOverall": false, "label": "GET Target URL", "isController": false}], "supportsControllersDiscrimination": true, "granularity": 60000, "maxX": 1.78160766E12, "title": "Latencies Over Time"}},
        getOptions: function() {
            return {
                series: {
                    lines: {
                        show: true
                    },
                    points: {
                        show: true
                    }
                },
                xaxis: {
                    mode: "time",
                    timeformat: getTimeFormat(this.data.result.granularity),
                    axisLabel: getElapsedTimeLabel(this.data.result.granularity),
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Average response latencies in ms",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                legend: {
                    noColumns: 2,
                    show: true,
                    container: '#legendLatenciesOverTime'
                },
                selection: {
                    mode: 'xy'
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: "%s : at %x Average latency was %y ms"
                }
            };
        },
        createGraph: function () {
            var data = this.data;
            var dataset = prepareData(data.result.series, $("#choicesLatenciesOverTime"));
            var options = this.getOptions();
            prepareOptions(options, data);
            $.plot($("#flotLatenciesOverTime"), dataset, options);
            // setup overview
            $.plot($("#overviewLatenciesOverTime"), dataset, prepareOverviewOptions(options));
        }
};

// Latencies Over Time
function refreshLatenciesOverTime(fixTimestamps) {
    var infos = latenciesOverTimeInfos;
    prepareSeries(infos.data);
    if(infos.data.result.series.length == 0) {
        setEmptyGraph("#bodyLatenciesOverTime");
        return;
    }
    if(fixTimestamps) {
        fixTimeStamps(infos.data.result.series, 19800000);
    }
    if(isGraph($("#flotLatenciesOverTime"))) {
        infos.createGraph();
    }else {
        var choiceContainer = $("#choicesLatenciesOverTime");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotLatenciesOverTime", "#overviewLatenciesOverTime");
        $('#footerLatenciesOverTime .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};

var connectTimeOverTimeInfos = {
        data: {"result": {"minY": 92.93000000000004, "minX": 1.78160766E12, "maxY": 92.93000000000004, "series": [{"data": [[1.78160766E12, 92.93000000000004]], "isOverall": false, "label": "GET Target URL", "isController": false}], "supportsControllersDiscrimination": true, "granularity": 60000, "maxX": 1.78160766E12, "title": "Connect Time Over Time"}},
        getOptions: function() {
            return {
                series: {
                    lines: {
                        show: true
                    },
                    points: {
                        show: true
                    }
                },
                xaxis: {
                    mode: "time",
                    timeformat: getTimeFormat(this.data.result.granularity),
                    axisLabel: getConnectTimeLabel(this.data.result.granularity),
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Average Connect Time in ms",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                legend: {
                    noColumns: 2,
                    show: true,
                    container: '#legendConnectTimeOverTime'
                },
                selection: {
                    mode: 'xy'
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: "%s : at %x Average connect time was %y ms"
                }
            };
        },
        createGraph: function () {
            var data = this.data;
            var dataset = prepareData(data.result.series, $("#choicesConnectTimeOverTime"));
            var options = this.getOptions();
            prepareOptions(options, data);
            $.plot($("#flotConnectTimeOverTime"), dataset, options);
            // setup overview
            $.plot($("#overviewConnectTimeOverTime"), dataset, prepareOverviewOptions(options));
        }
};

// Connect Time Over Time
function refreshConnectTimeOverTime(fixTimestamps) {
    var infos = connectTimeOverTimeInfos;
    prepareSeries(infos.data);
    if(infos.data.result.series.length == 0) {
        setEmptyGraph("#bodyConnectTimeOverTime");
        return;
    }
    if(fixTimestamps) {
        fixTimeStamps(infos.data.result.series, 19800000);
    }
    if(isGraph($("#flotConnectTimeOverTime"))) {
        infos.createGraph();
    }else {
        var choiceContainer = $("#choicesConnectTimeOverTime");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotConnectTimeOverTime", "#overviewConnectTimeOverTime");
        $('#footerConnectTimeOverTime .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};

var responseTimePercentilesOverTimeInfos = {
        data: {"result": {"minY": 239.0, "minX": 1.78160766E12, "maxY": 1779.0, "series": [{"data": [[1.78160766E12, 1779.0]], "isOverall": false, "label": "Max", "isController": false}, {"data": [[1.78160766E12, 239.0]], "isOverall": false, "label": "Min", "isController": false}, {"data": [[1.78160766E12, 599.0000000000005]], "isOverall": false, "label": "90th percentile", "isController": false}, {"data": [[1.78160766E12, 1773.8999999999974]], "isOverall": false, "label": "99th percentile", "isController": false}, {"data": [[1.78160766E12, 294.0]], "isOverall": false, "label": "Median", "isController": false}, {"data": [[1.78160766E12, 873.9499999999994]], "isOverall": false, "label": "95th percentile", "isController": false}], "supportsControllersDiscrimination": false, "granularity": 60000, "maxX": 1.78160766E12, "title": "Response Time Percentiles Over Time (successful requests only)"}},
        getOptions: function() {
            return {
                series: {
                    lines: {
                        show: true,
                        fill: true
                    },
                    points: {
                        show: true
                    }
                },
                xaxis: {
                    mode: "time",
                    timeformat: getTimeFormat(this.data.result.granularity),
                    axisLabel: getElapsedTimeLabel(this.data.result.granularity),
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Response Time in ms",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                legend: {
                    noColumns: 2,
                    show: true,
                    container: '#legendResponseTimePercentilesOverTime'
                },
                selection: {
                    mode: 'xy'
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: "%s : at %x Response time was %y ms"
                }
            };
        },
        createGraph: function () {
            var data = this.data;
            var dataset = prepareData(data.result.series, $("#choicesResponseTimePercentilesOverTime"));
            var options = this.getOptions();
            prepareOptions(options, data);
            $.plot($("#flotResponseTimePercentilesOverTime"), dataset, options);
            // setup overview
            $.plot($("#overviewResponseTimePercentilesOverTime"), dataset, prepareOverviewOptions(options));
        }
};

// Response Time Percentiles Over Time
function refreshResponseTimePercentilesOverTime(fixTimestamps) {
    var infos = responseTimePercentilesOverTimeInfos;
    prepareSeries(infos.data);
    if(fixTimestamps) {
        fixTimeStamps(infos.data.result.series, 19800000);
    }
    if(isGraph($("#flotResponseTimePercentilesOverTime"))) {
        infos.createGraph();
    }else {
        var choiceContainer = $("#choicesResponseTimePercentilesOverTime");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotResponseTimePercentilesOverTime", "#overviewResponseTimePercentilesOverTime");
        $('#footerResponseTimePercentilesOverTime .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};


var responseTimeVsRequestInfos = {
    data: {"result": {"minY": 284.0, "minX": 2.0, "maxY": 542.0, "series": [{"data": [[8.0, 304.5], [2.0, 542.0], [9.0, 311.0], [10.0, 284.0], [12.0, 312.5]], "isOverall": false, "label": "Successes", "isController": false}], "supportsControllersDiscrimination": false, "granularity": 1000, "maxX": 12.0, "title": "Response Time Vs Request"}},
    getOptions: function() {
        return {
            series: {
                lines: {
                    show: false
                },
                points: {
                    show: true
                }
            },
            xaxis: {
                axisLabel: "Global number of requests per second",
                axisLabelUseCanvas: true,
                axisLabelFontSizePixels: 12,
                axisLabelFontFamily: 'Verdana, Arial',
                axisLabelPadding: 20,
            },
            yaxis: {
                axisLabel: "Median Response Time in ms",
                axisLabelUseCanvas: true,
                axisLabelFontSizePixels: 12,
                axisLabelFontFamily: 'Verdana, Arial',
                axisLabelPadding: 20,
            },
            legend: {
                noColumns: 2,
                show: true,
                container: '#legendResponseTimeVsRequest'
            },
            selection: {
                mode: 'xy'
            },
            grid: {
                hoverable: true // IMPORTANT! this is needed for tooltip to work
            },
            tooltip: true,
            tooltipOpts: {
                content: "%s : Median response time at %x req/s was %y ms"
            },
            colors: ["#9ACD32", "#FF6347"]
        };
    },
    createGraph: function () {
        var data = this.data;
        var dataset = prepareData(data.result.series, $("#choicesResponseTimeVsRequest"));
        var options = this.getOptions();
        prepareOptions(options, data);
        $.plot($("#flotResponseTimeVsRequest"), dataset, options);
        // setup overview
        $.plot($("#overviewResponseTimeVsRequest"), dataset, prepareOverviewOptions(options));

    }
};

// Response Time vs Request
function refreshResponseTimeVsRequest() {
    var infos = responseTimeVsRequestInfos;
    prepareSeries(infos.data);
    if (isGraph($("#flotResponseTimeVsRequest"))){
        infos.createGraph();
    }else{
        var choiceContainer = $("#choicesResponseTimeVsRequest");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotResponseTimeVsRequest", "#overviewResponseTimeVsRequest");
        $('#footerResponseRimeVsRequest .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};


var latenciesVsRequestInfos = {
    data: {"result": {"minY": 137.5, "minX": 2.0, "maxY": 145.0, "series": [{"data": [[8.0, 137.5], [2.0, 145.0], [9.0, 141.0], [10.0, 144.0], [12.0, 139.0]], "isOverall": false, "label": "Successes", "isController": false}], "supportsControllersDiscrimination": false, "granularity": 1000, "maxX": 12.0, "title": "Latencies Vs Request"}},
    getOptions: function() {
        return{
            series: {
                lines: {
                    show: false
                },
                points: {
                    show: true
                }
            },
            xaxis: {
                axisLabel: "Global number of requests per second",
                axisLabelUseCanvas: true,
                axisLabelFontSizePixels: 12,
                axisLabelFontFamily: 'Verdana, Arial',
                axisLabelPadding: 20,
            },
            yaxis: {
                axisLabel: "Median Latency in ms",
                axisLabelUseCanvas: true,
                axisLabelFontSizePixels: 12,
                axisLabelFontFamily: 'Verdana, Arial',
                axisLabelPadding: 20,
            },
            legend: { noColumns: 2,show: true, container: '#legendLatencyVsRequest' },
            selection: {
                mode: 'xy'
            },
            grid: {
                hoverable: true // IMPORTANT! this is needed for tooltip to work
            },
            tooltip: true,
            tooltipOpts: {
                content: "%s : Median Latency time at %x req/s was %y ms"
            },
            colors: ["#9ACD32", "#FF6347"]
        };
    },
    createGraph: function () {
        var data = this.data;
        var dataset = prepareData(data.result.series, $("#choicesLatencyVsRequest"));
        var options = this.getOptions();
        prepareOptions(options, data);
        $.plot($("#flotLatenciesVsRequest"), dataset, options);
        // setup overview
        $.plot($("#overviewLatenciesVsRequest"), dataset, prepareOverviewOptions(options));
    }
};

// Latencies vs Request
function refreshLatenciesVsRequest() {
        var infos = latenciesVsRequestInfos;
        prepareSeries(infos.data);
        if(isGraph($("#flotLatenciesVsRequest"))){
            infos.createGraph();
        }else{
            var choiceContainer = $("#choicesLatencyVsRequest");
            createLegend(choiceContainer, infos);
            infos.createGraph();
            setGraphZoomable("#flotLatenciesVsRequest", "#overviewLatenciesVsRequest");
            $('#footerLatenciesVsRequest .legendColorBox > div').each(function(i){
                $(this).clone().prependTo(choiceContainer.find("li").eq(i));
            });
        }
};

var hitsPerSecondInfos = {
        data: {"result": {"minY": 1.6666666666666667, "minX": 1.78160766E12, "maxY": 1.6666666666666667, "series": [{"data": [[1.78160766E12, 1.6666666666666667]], "isOverall": false, "label": "hitsPerSecond", "isController": false}], "supportsControllersDiscrimination": false, "granularity": 60000, "maxX": 1.78160766E12, "title": "Hits Per Second"}},
        getOptions: function() {
            return {
                series: {
                    lines: {
                        show: true
                    },
                    points: {
                        show: true
                    }
                },
                xaxis: {
                    mode: "time",
                    timeformat: getTimeFormat(this.data.result.granularity),
                    axisLabel: getElapsedTimeLabel(this.data.result.granularity),
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Number of hits / sec",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20
                },
                legend: {
                    noColumns: 2,
                    show: true,
                    container: "#legendHitsPerSecond"
                },
                selection: {
                    mode : 'xy'
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: "%s at %x was %y.2 hits/sec"
                }
            };
        },
        createGraph: function createGraph() {
            var data = this.data;
            var dataset = prepareData(data.result.series, $("#choicesHitsPerSecond"));
            var options = this.getOptions();
            prepareOptions(options, data);
            $.plot($("#flotHitsPerSecond"), dataset, options);
            // setup overview
            $.plot($("#overviewHitsPerSecond"), dataset, prepareOverviewOptions(options));
        }
};

// Hits per second
function refreshHitsPerSecond(fixTimestamps) {
    var infos = hitsPerSecondInfos;
    prepareSeries(infos.data);
    if(fixTimestamps) {
        fixTimeStamps(infos.data.result.series, 19800000);
    }
    if (isGraph($("#flotHitsPerSecond"))){
        infos.createGraph();
    }else{
        var choiceContainer = $("#choicesHitsPerSecond");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotHitsPerSecond", "#overviewHitsPerSecond");
        $('#footerHitsPerSecond .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
}

var codesPerSecondInfos = {
        data: {"result": {"minY": 1.6666666666666667, "minX": 1.78160766E12, "maxY": 1.6666666666666667, "series": [{"data": [[1.78160766E12, 1.6666666666666667]], "isOverall": false, "label": "200", "isController": false}], "supportsControllersDiscrimination": false, "granularity": 60000, "maxX": 1.78160766E12, "title": "Codes Per Second"}},
        getOptions: function(){
            return {
                series: {
                    lines: {
                        show: true
                    },
                    points: {
                        show: true
                    }
                },
                xaxis: {
                    mode: "time",
                    timeformat: getTimeFormat(this.data.result.granularity),
                    axisLabel: getElapsedTimeLabel(this.data.result.granularity),
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Number of responses / sec",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                legend: {
                    noColumns: 2,
                    show: true,
                    container: "#legendCodesPerSecond"
                },
                selection: {
                    mode: 'xy'
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: "Number of Response Codes %s at %x was %y.2 responses / sec"
                }
            };
        },
    createGraph: function() {
        var data = this.data;
        var dataset = prepareData(data.result.series, $("#choicesCodesPerSecond"));
        var options = this.getOptions();
        prepareOptions(options, data);
        $.plot($("#flotCodesPerSecond"), dataset, options);
        // setup overview
        $.plot($("#overviewCodesPerSecond"), dataset, prepareOverviewOptions(options));
    }
};

// Codes per second
function refreshCodesPerSecond(fixTimestamps) {
    var infos = codesPerSecondInfos;
    prepareSeries(infos.data);
    if(fixTimestamps) {
        fixTimeStamps(infos.data.result.series, 19800000);
    }
    if(isGraph($("#flotCodesPerSecond"))){
        infos.createGraph();
    }else{
        var choiceContainer = $("#choicesCodesPerSecond");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotCodesPerSecond", "#overviewCodesPerSecond");
        $('#footerCodesPerSecond .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};

var transactionsPerSecondInfos = {
        data: {"result": {"minY": 1.6666666666666667, "minX": 1.78160766E12, "maxY": 1.6666666666666667, "series": [{"data": [[1.78160766E12, 1.6666666666666667]], "isOverall": false, "label": "GET Target URL-success", "isController": false}], "supportsControllersDiscrimination": true, "granularity": 60000, "maxX": 1.78160766E12, "title": "Transactions Per Second"}},
        getOptions: function(){
            return {
                series: {
                    lines: {
                        show: true
                    },
                    points: {
                        show: true
                    }
                },
                xaxis: {
                    mode: "time",
                    timeformat: getTimeFormat(this.data.result.granularity),
                    axisLabel: getElapsedTimeLabel(this.data.result.granularity),
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Number of transactions / sec",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20
                },
                legend: {
                    noColumns: 2,
                    show: true,
                    container: "#legendTransactionsPerSecond"
                },
                selection: {
                    mode: 'xy'
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: "%s at %x was %y transactions / sec"
                }
            };
        },
    createGraph: function () {
        var data = this.data;
        var dataset = prepareData(data.result.series, $("#choicesTransactionsPerSecond"));
        var options = this.getOptions();
        prepareOptions(options, data);
        $.plot($("#flotTransactionsPerSecond"), dataset, options);
        // setup overview
        $.plot($("#overviewTransactionsPerSecond"), dataset, prepareOverviewOptions(options));
    }
};

// Transactions per second
function refreshTransactionsPerSecond(fixTimestamps) {
    var infos = transactionsPerSecondInfos;
    prepareSeries(infos.data);
    if(infos.data.result.series.length == 0) {
        setEmptyGraph("#bodyTransactionsPerSecond");
        return;
    }
    if(fixTimestamps) {
        fixTimeStamps(infos.data.result.series, 19800000);
    }
    if(isGraph($("#flotTransactionsPerSecond"))){
        infos.createGraph();
    }else{
        var choiceContainer = $("#choicesTransactionsPerSecond");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotTransactionsPerSecond", "#overviewTransactionsPerSecond");
        $('#footerTransactionsPerSecond .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};

var totalTPSInfos = {
        data: {"result": {"minY": 1.6666666666666667, "minX": 1.78160766E12, "maxY": 1.6666666666666667, "series": [{"data": [[1.78160766E12, 1.6666666666666667]], "isOverall": false, "label": "Transaction-success", "isController": false}, {"data": [], "isOverall": false, "label": "Transaction-failure", "isController": false}], "supportsControllersDiscrimination": true, "granularity": 60000, "maxX": 1.78160766E12, "title": "Total Transactions Per Second"}},
        getOptions: function(){
            return {
                series: {
                    lines: {
                        show: true
                    },
                    points: {
                        show: true
                    }
                },
                xaxis: {
                    mode: "time",
                    timeformat: getTimeFormat(this.data.result.granularity),
                    axisLabel: getElapsedTimeLabel(this.data.result.granularity),
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Number of transactions / sec",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20
                },
                legend: {
                    noColumns: 2,
                    show: true,
                    container: "#legendTotalTPS"
                },
                selection: {
                    mode: 'xy'
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: "%s at %x was %y transactions / sec"
                },
                colors: ["#9ACD32", "#FF6347"]
            };
        },
    createGraph: function () {
        var data = this.data;
        var dataset = prepareData(data.result.series, $("#choicesTotalTPS"));
        var options = this.getOptions();
        prepareOptions(options, data);
        $.plot($("#flotTotalTPS"), dataset, options);
        // setup overview
        $.plot($("#overviewTotalTPS"), dataset, prepareOverviewOptions(options));
    }
};

// Total Transactions per second
function refreshTotalTPS(fixTimestamps) {
    var infos = totalTPSInfos;
    // We want to ignore seriesFilter
    prepareSeries(infos.data, false, true);
    if(fixTimestamps) {
        fixTimeStamps(infos.data.result.series, 19800000);
    }
    if(isGraph($("#flotTotalTPS"))){
        infos.createGraph();
    }else{
        var choiceContainer = $("#choicesTotalTPS");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotTotalTPS", "#overviewTotalTPS");
        $('#footerTotalTPS .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};

// Collapse the graph matching the specified DOM element depending the collapsed
// status
function collapse(elem, collapsed){
    if(collapsed){
        $(elem).parent().find(".fa-chevron-up").removeClass("fa-chevron-up").addClass("fa-chevron-down");
    } else {
        $(elem).parent().find(".fa-chevron-down").removeClass("fa-chevron-down").addClass("fa-chevron-up");
        if (elem.id == "bodyBytesThroughputOverTime") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshBytesThroughputOverTime(true);
            }
            document.location.href="#bytesThroughputOverTime";
        } else if (elem.id == "bodyLatenciesOverTime") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshLatenciesOverTime(true);
            }
            document.location.href="#latenciesOverTime";
        } else if (elem.id == "bodyCustomGraph") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshCustomGraph(true);
            }
            document.location.href="#responseCustomGraph";
        } else if (elem.id == "bodyConnectTimeOverTime") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshConnectTimeOverTime(true);
            }
            document.location.href="#connectTimeOverTime";
        } else if (elem.id == "bodyResponseTimePercentilesOverTime") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshResponseTimePercentilesOverTime(true);
            }
            document.location.href="#responseTimePercentilesOverTime";
        } else if (elem.id == "bodyResponseTimeDistribution") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshResponseTimeDistribution();
            }
            document.location.href="#responseTimeDistribution" ;
        } else if (elem.id == "bodySyntheticResponseTimeDistribution") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshSyntheticResponseTimeDistribution();
            }
            document.location.href="#syntheticResponseTimeDistribution" ;
        } else if (elem.id == "bodyActiveThreadsOverTime") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshActiveThreadsOverTime(true);
            }
            document.location.href="#activeThreadsOverTime";
        } else if (elem.id == "bodyTimeVsThreads") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshTimeVsThreads();
            }
            document.location.href="#timeVsThreads" ;
        } else if (elem.id == "bodyCodesPerSecond") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshCodesPerSecond(true);
            }
            document.location.href="#codesPerSecond";
        } else if (elem.id == "bodyTransactionsPerSecond") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshTransactionsPerSecond(true);
            }
            document.location.href="#transactionsPerSecond";
        } else if (elem.id == "bodyTotalTPS") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshTotalTPS(true);
            }
            document.location.href="#totalTPS";
        } else if (elem.id == "bodyResponseTimeVsRequest") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshResponseTimeVsRequest();
            }
            document.location.href="#responseTimeVsRequest";
        } else if (elem.id == "bodyLatenciesVsRequest") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshLatenciesVsRequest();
            }
            document.location.href="#latencyVsRequest";
        }
    }
}

/*
 * Activates or deactivates all series of the specified graph (represented by id parameter)
 * depending on checked argument.
 */
function toggleAll(id, checked){
    var placeholder = document.getElementById(id);

    var cases = $(placeholder).find(':checkbox');
    cases.prop('checked', checked);
    $(cases).parent().children().children().toggleClass("legend-disabled", !checked);

    var choiceContainer;
    if ( id == "choicesBytesThroughputOverTime"){
        choiceContainer = $("#choicesBytesThroughputOverTime");
        refreshBytesThroughputOverTime(false);
    } else if(id == "choicesResponseTimesOverTime"){
        choiceContainer = $("#choicesResponseTimesOverTime");
        refreshResponseTimeOverTime(false);
    }else if(id == "choicesResponseCustomGraph"){
        choiceContainer = $("#choicesResponseCustomGraph");
        refreshCustomGraph(false);
    } else if ( id == "choicesLatenciesOverTime"){
        choiceContainer = $("#choicesLatenciesOverTime");
        refreshLatenciesOverTime(false);
    } else if ( id == "choicesConnectTimeOverTime"){
        choiceContainer = $("#choicesConnectTimeOverTime");
        refreshConnectTimeOverTime(false);
    } else if ( id == "choicesResponseTimePercentilesOverTime"){
        choiceContainer = $("#choicesResponseTimePercentilesOverTime");
        refreshResponseTimePercentilesOverTime(false);
    } else if ( id == "choicesResponseTimePercentiles"){
        choiceContainer = $("#choicesResponseTimePercentiles");
        refreshResponseTimePercentiles();
    } else if(id == "choicesActiveThreadsOverTime"){
        choiceContainer = $("#choicesActiveThreadsOverTime");
        refreshActiveThreadsOverTime(false);
    } else if ( id == "choicesTimeVsThreads"){
        choiceContainer = $("#choicesTimeVsThreads");
        refreshTimeVsThreads();
    } else if ( id == "choicesSyntheticResponseTimeDistribution"){
        choiceContainer = $("#choicesSyntheticResponseTimeDistribution");
        refreshSyntheticResponseTimeDistribution();
    } else if ( id == "choicesResponseTimeDistribution"){
        choiceContainer = $("#choicesResponseTimeDistribution");
        refreshResponseTimeDistribution();
    } else if ( id == "choicesHitsPerSecond"){
        choiceContainer = $("#choicesHitsPerSecond");
        refreshHitsPerSecond(false);
    } else if(id == "choicesCodesPerSecond"){
        choiceContainer = $("#choicesCodesPerSecond");
        refreshCodesPerSecond(false);
    } else if ( id == "choicesTransactionsPerSecond"){
        choiceContainer = $("#choicesTransactionsPerSecond");
        refreshTransactionsPerSecond(false);
    } else if ( id == "choicesTotalTPS"){
        choiceContainer = $("#choicesTotalTPS");
        refreshTotalTPS(false);
    } else if ( id == "choicesResponseTimeVsRequest"){
        choiceContainer = $("#choicesResponseTimeVsRequest");
        refreshResponseTimeVsRequest();
    } else if ( id == "choicesLatencyVsRequest"){
        choiceContainer = $("#choicesLatencyVsRequest");
        refreshLatenciesVsRequest();
    }
    var color = checked ? "black" : "#818181";
    if(choiceContainer != null) {
        choiceContainer.find("label").each(function(){
            this.style.color = color;
        });
    }
}

