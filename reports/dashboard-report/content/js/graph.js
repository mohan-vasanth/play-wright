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
        data: {"result": {"minY": 52.0, "minX": 0.0, "maxY": 2859.0, "series": [{"data": [[0.0, 52.0], [0.1, 52.0], [0.2, 52.0], [0.3, 52.0], [0.4, 53.0], [0.5, 53.0], [0.6, 53.0], [0.7, 53.0], [0.8, 54.0], [0.9, 54.0], [1.0, 54.0], [1.1, 54.0], [1.2, 54.0], [1.3, 54.0], [1.4, 54.0], [1.5, 54.0], [1.6, 55.0], [1.7, 55.0], [1.8, 55.0], [1.9, 55.0], [2.0, 55.0], [2.1, 55.0], [2.2, 55.0], [2.3, 55.0], [2.4, 55.0], [2.5, 55.0], [2.6, 55.0], [2.7, 55.0], [2.8, 56.0], [2.9, 56.0], [3.0, 56.0], [3.1, 56.0], [3.2, 56.0], [3.3, 56.0], [3.4, 56.0], [3.5, 56.0], [3.6, 57.0], [3.7, 57.0], [3.8, 57.0], [3.9, 57.0], [4.0, 58.0], [4.1, 58.0], [4.2, 58.0], [4.3, 58.0], [4.4, 58.0], [4.5, 58.0], [4.6, 58.0], [4.7, 58.0], [4.8, 59.0], [4.9, 59.0], [5.0, 59.0], [5.1, 59.0], [5.2, 59.0], [5.3, 59.0], [5.4, 59.0], [5.5, 59.0], [5.6, 60.0], [5.7, 60.0], [5.8, 60.0], [5.9, 60.0], [6.0, 60.0], [6.1, 60.0], [6.2, 60.0], [6.3, 60.0], [6.4, 61.0], [6.5, 61.0], [6.6, 61.0], [6.7, 61.0], [6.8, 61.0], [6.9, 61.0], [7.0, 61.0], [7.1, 61.0], [7.2, 63.0], [7.3, 63.0], [7.4, 63.0], [7.5, 63.0], [7.6, 64.0], [7.7, 64.0], [7.8, 64.0], [7.9, 64.0], [8.0, 64.0], [8.1, 64.0], [8.2, 64.0], [8.3, 64.0], [8.4, 65.0], [8.5, 65.0], [8.6, 65.0], [8.7, 65.0], [8.8, 68.0], [8.9, 68.0], [9.0, 68.0], [9.1, 68.0], [9.2, 68.0], [9.3, 68.0], [9.4, 68.0], [9.5, 68.0], [9.6, 68.0], [9.7, 68.0], [9.8, 68.0], [9.9, 68.0], [10.0, 69.0], [10.1, 69.0], [10.2, 69.0], [10.3, 69.0], [10.4, 71.0], [10.5, 71.0], [10.6, 71.0], [10.7, 71.0], [10.8, 91.0], [10.9, 91.0], [11.0, 91.0], [11.1, 91.0], [11.2, 91.0], [11.3, 97.0], [11.4, 97.0], [11.5, 97.0], [11.6, 97.0], [11.7, 98.0], [11.8, 98.0], [11.9, 98.0], [12.0, 98.0], [12.1, 99.0], [12.2, 99.0], [12.3, 99.0], [12.4, 99.0], [12.5, 100.0], [12.6, 100.0], [12.7, 100.0], [12.8, 100.0], [12.9, 102.0], [13.0, 102.0], [13.1, 102.0], [13.2, 102.0], [13.3, 104.0], [13.4, 104.0], [13.5, 104.0], [13.6, 104.0], [13.7, 105.0], [13.8, 105.0], [13.9, 105.0], [14.0, 105.0], [14.1, 105.0], [14.2, 105.0], [14.3, 105.0], [14.4, 105.0], [14.5, 106.0], [14.6, 106.0], [14.7, 106.0], [14.8, 106.0], [14.9, 106.0], [15.0, 106.0], [15.1, 106.0], [15.2, 106.0], [15.3, 107.0], [15.4, 107.0], [15.5, 107.0], [15.6, 107.0], [15.7, 108.0], [15.8, 108.0], [15.9, 108.0], [16.0, 108.0], [16.1, 108.0], [16.2, 108.0], [16.3, 108.0], [16.4, 108.0], [16.5, 109.0], [16.6, 109.0], [16.7, 109.0], [16.8, 109.0], [16.9, 109.0], [17.0, 109.0], [17.1, 109.0], [17.2, 109.0], [17.3, 111.0], [17.4, 111.0], [17.5, 111.0], [17.6, 111.0], [17.7, 115.0], [17.8, 115.0], [17.9, 115.0], [18.0, 115.0], [18.1, 116.0], [18.2, 116.0], [18.3, 116.0], [18.4, 116.0], [18.5, 118.0], [18.6, 118.0], [18.7, 118.0], [18.8, 123.0], [18.9, 123.0], [19.0, 123.0], [19.1, 123.0], [19.2, 124.0], [19.3, 124.0], [19.4, 124.0], [19.5, 124.0], [19.6, 124.0], [19.7, 124.0], [19.8, 124.0], [19.9, 124.0], [20.0, 125.0], [20.1, 125.0], [20.2, 125.0], [20.3, 125.0], [20.4, 126.0], [20.5, 126.0], [20.6, 126.0], [20.7, 126.0], [20.8, 127.0], [20.9, 127.0], [21.0, 127.0], [21.1, 127.0], [21.2, 127.0], [21.3, 127.0], [21.4, 127.0], [21.5, 127.0], [21.6, 129.0], [21.7, 129.0], [21.8, 129.0], [21.9, 129.0], [22.0, 130.0], [22.1, 130.0], [22.2, 130.0], [22.3, 130.0], [22.4, 131.0], [22.5, 131.0], [22.6, 131.0], [22.7, 131.0], [22.8, 132.0], [22.9, 132.0], [23.0, 132.0], [23.1, 132.0], [23.2, 133.0], [23.3, 133.0], [23.4, 133.0], [23.5, 133.0], [23.6, 135.0], [23.7, 135.0], [23.8, 135.0], [23.9, 135.0], [24.0, 137.0], [24.1, 137.0], [24.2, 137.0], [24.3, 137.0], [24.4, 138.0], [24.5, 138.0], [24.6, 138.0], [24.7, 138.0], [24.8, 140.0], [24.9, 140.0], [25.0, 140.0], [25.1, 140.0], [25.2, 141.0], [25.3, 141.0], [25.4, 141.0], [25.5, 141.0], [25.6, 142.0], [25.7, 142.0], [25.8, 142.0], [25.9, 142.0], [26.0, 146.0], [26.1, 146.0], [26.2, 146.0], [26.3, 146.0], [26.4, 147.0], [26.5, 147.0], [26.6, 147.0], [26.7, 147.0], [26.8, 147.0], [26.9, 147.0], [27.0, 147.0], [27.1, 147.0], [27.2, 150.0], [27.3, 150.0], [27.4, 150.0], [27.5, 150.0], [27.6, 151.0], [27.7, 151.0], [27.8, 151.0], [27.9, 151.0], [28.0, 151.0], [28.1, 151.0], [28.2, 151.0], [28.3, 151.0], [28.4, 152.0], [28.5, 152.0], [28.6, 152.0], [28.7, 152.0], [28.8, 152.0], [28.9, 152.0], [29.0, 152.0], [29.1, 152.0], [29.2, 153.0], [29.3, 153.0], [29.4, 153.0], [29.5, 153.0], [29.6, 153.0], [29.7, 153.0], [29.8, 153.0], [29.9, 153.0], [30.0, 154.0], [30.1, 154.0], [30.2, 154.0], [30.3, 154.0], [30.4, 156.0], [30.5, 156.0], [30.6, 156.0], [30.7, 156.0], [30.8, 157.0], [30.9, 157.0], [31.0, 157.0], [31.1, 157.0], [31.2, 158.0], [31.3, 158.0], [31.4, 158.0], [31.5, 158.0], [31.6, 158.0], [31.7, 158.0], [31.8, 158.0], [31.9, 158.0], [32.0, 159.0], [32.1, 159.0], [32.2, 159.0], [32.3, 159.0], [32.4, 160.0], [32.5, 160.0], [32.6, 160.0], [32.7, 160.0], [32.8, 160.0], [32.9, 160.0], [33.0, 160.0], [33.1, 160.0], [33.2, 161.0], [33.3, 161.0], [33.4, 161.0], [33.5, 161.0], [33.6, 161.0], [33.7, 161.0], [33.8, 161.0], [33.9, 161.0], [34.0, 161.0], [34.1, 161.0], [34.2, 161.0], [34.3, 161.0], [34.4, 161.0], [34.5, 161.0], [34.6, 161.0], [34.7, 161.0], [34.8, 162.0], [34.9, 162.0], [35.0, 162.0], [35.1, 162.0], [35.2, 162.0], [35.3, 162.0], [35.4, 162.0], [35.5, 162.0], [35.6, 162.0], [35.7, 162.0], [35.8, 162.0], [35.9, 162.0], [36.0, 164.0], [36.1, 164.0], [36.2, 164.0], [36.3, 164.0], [36.4, 164.0], [36.5, 164.0], [36.6, 164.0], [36.7, 164.0], [36.8, 167.0], [36.9, 167.0], [37.0, 167.0], [37.1, 167.0], [37.2, 167.0], [37.3, 167.0], [37.4, 167.0], [37.5, 167.0], [37.6, 167.0], [37.7, 167.0], [37.8, 167.0], [37.9, 167.0], [38.0, 168.0], [38.1, 168.0], [38.2, 168.0], [38.3, 168.0], [38.4, 168.0], [38.5, 168.0], [38.6, 168.0], [38.7, 168.0], [38.8, 168.0], [38.9, 168.0], [39.0, 168.0], [39.1, 168.0], [39.2, 171.0], [39.3, 171.0], [39.4, 171.0], [39.5, 171.0], [39.6, 172.0], [39.7, 172.0], [39.8, 172.0], [39.9, 172.0], [40.0, 174.0], [40.1, 174.0], [40.2, 174.0], [40.3, 174.0], [40.4, 175.0], [40.5, 175.0], [40.6, 175.0], [40.7, 175.0], [40.8, 177.0], [40.9, 177.0], [41.0, 177.0], [41.1, 177.0], [41.2, 179.0], [41.3, 179.0], [41.4, 179.0], [41.5, 179.0], [41.6, 183.0], [41.7, 183.0], [41.8, 183.0], [41.9, 183.0], [42.0, 186.0], [42.1, 186.0], [42.2, 186.0], [42.3, 186.0], [42.4, 191.0], [42.5, 191.0], [42.6, 191.0], [42.7, 191.0], [42.8, 194.0], [42.9, 194.0], [43.0, 194.0], [43.1, 194.0], [43.2, 195.0], [43.3, 195.0], [43.4, 195.0], [43.5, 195.0], [43.6, 200.0], [43.7, 200.0], [43.8, 200.0], [43.9, 200.0], [44.0, 201.0], [44.1, 201.0], [44.2, 201.0], [44.3, 201.0], [44.4, 202.0], [44.5, 202.0], [44.6, 202.0], [44.7, 202.0], [44.8, 203.0], [44.9, 203.0], [45.0, 203.0], [45.1, 203.0], [45.2, 203.0], [45.3, 203.0], [45.4, 203.0], [45.5, 203.0], [45.6, 204.0], [45.7, 204.0], [45.8, 204.0], [45.9, 204.0], [46.0, 205.0], [46.1, 205.0], [46.2, 205.0], [46.3, 205.0], [46.4, 205.0], [46.5, 205.0], [46.6, 205.0], [46.7, 205.0], [46.8, 205.0], [46.9, 205.0], [47.0, 205.0], [47.1, 205.0], [47.2, 205.0], [47.3, 205.0], [47.4, 205.0], [47.5, 205.0], [47.6, 206.0], [47.7, 206.0], [47.8, 206.0], [47.9, 206.0], [48.0, 206.0], [48.1, 206.0], [48.2, 206.0], [48.3, 206.0], [48.4, 207.0], [48.5, 207.0], [48.6, 207.0], [48.7, 207.0], [48.8, 207.0], [48.9, 207.0], [49.0, 207.0], [49.1, 207.0], [49.2, 208.0], [49.3, 208.0], [49.4, 208.0], [49.5, 208.0], [49.6, 209.0], [49.7, 209.0], [49.8, 209.0], [49.9, 209.0], [50.0, 209.0], [50.1, 209.0], [50.2, 209.0], [50.3, 209.0], [50.4, 210.0], [50.5, 210.0], [50.6, 210.0], [50.7, 210.0], [50.8, 211.0], [50.9, 211.0], [51.0, 211.0], [51.1, 211.0], [51.2, 211.0], [51.3, 211.0], [51.4, 211.0], [51.5, 211.0], [51.6, 211.0], [51.7, 211.0], [51.8, 211.0], [51.9, 211.0], [52.0, 212.0], [52.1, 212.0], [52.2, 212.0], [52.3, 212.0], [52.4, 213.0], [52.5, 213.0], [52.6, 213.0], [52.7, 213.0], [52.8, 214.0], [52.9, 214.0], [53.0, 214.0], [53.1, 214.0], [53.2, 214.0], [53.3, 214.0], [53.4, 214.0], [53.5, 214.0], [53.6, 215.0], [53.7, 215.0], [53.8, 215.0], [53.9, 215.0], [54.0, 215.0], [54.1, 215.0], [54.2, 215.0], [54.3, 215.0], [54.4, 215.0], [54.5, 215.0], [54.6, 215.0], [54.7, 215.0], [54.8, 215.0], [54.9, 215.0], [55.0, 215.0], [55.1, 215.0], [55.2, 217.0], [55.3, 217.0], [55.4, 217.0], [55.5, 217.0], [55.6, 217.0], [55.7, 217.0], [55.8, 217.0], [55.9, 217.0], [56.0, 219.0], [56.1, 219.0], [56.2, 219.0], [56.3, 219.0], [56.4, 219.0], [56.5, 219.0], [56.6, 219.0], [56.7, 219.0], [56.8, 219.0], [56.9, 219.0], [57.0, 219.0], [57.1, 219.0], [57.2, 220.0], [57.3, 220.0], [57.4, 220.0], [57.5, 220.0], [57.6, 221.0], [57.7, 221.0], [57.8, 221.0], [57.9, 221.0], [58.0, 221.0], [58.1, 221.0], [58.2, 221.0], [58.3, 221.0], [58.4, 221.0], [58.5, 221.0], [58.6, 221.0], [58.7, 221.0], [58.8, 221.0], [58.9, 221.0], [59.0, 221.0], [59.1, 221.0], [59.2, 222.0], [59.3, 222.0], [59.4, 222.0], [59.5, 222.0], [59.6, 222.0], [59.7, 222.0], [59.8, 222.0], [59.9, 222.0], [60.0, 222.0], [60.1, 222.0], [60.2, 222.0], [60.3, 222.0], [60.4, 222.0], [60.5, 222.0], [60.6, 222.0], [60.7, 222.0], [60.8, 222.0], [60.9, 222.0], [61.0, 222.0], [61.1, 222.0], [61.2, 223.0], [61.3, 223.0], [61.4, 223.0], [61.5, 223.0], [61.6, 223.0], [61.7, 223.0], [61.8, 223.0], [61.9, 223.0], [62.0, 223.0], [62.1, 223.0], [62.2, 223.0], [62.3, 223.0], [62.4, 223.0], [62.5, 223.0], [62.6, 223.0], [62.7, 223.0], [62.8, 225.0], [62.9, 225.0], [63.0, 225.0], [63.1, 225.0], [63.2, 225.0], [63.3, 225.0], [63.4, 225.0], [63.5, 225.0], [63.6, 226.0], [63.7, 226.0], [63.8, 226.0], [63.9, 226.0], [64.0, 229.0], [64.1, 229.0], [64.2, 229.0], [64.3, 229.0], [64.4, 229.0], [64.5, 229.0], [64.6, 229.0], [64.7, 229.0], [64.8, 231.0], [64.9, 231.0], [65.0, 231.0], [65.1, 231.0], [65.2, 234.0], [65.3, 234.0], [65.4, 234.0], [65.5, 234.0], [65.6, 234.0], [65.7, 234.0], [65.8, 234.0], [65.9, 234.0], [66.0, 236.0], [66.1, 236.0], [66.2, 236.0], [66.3, 236.0], [66.4, 237.0], [66.5, 237.0], [66.6, 237.0], [66.7, 237.0], [66.8, 239.0], [66.9, 239.0], [67.0, 239.0], [67.1, 239.0], [67.2, 241.0], [67.3, 241.0], [67.4, 241.0], [67.5, 241.0], [67.6, 242.0], [67.7, 242.0], [67.8, 242.0], [67.9, 242.0], [68.0, 245.0], [68.1, 245.0], [68.2, 245.0], [68.3, 245.0], [68.4, 247.0], [68.5, 247.0], [68.6, 247.0], [68.7, 247.0], [68.8, 249.0], [68.9, 249.0], [69.0, 249.0], [69.1, 249.0], [69.2, 253.0], [69.3, 253.0], [69.4, 253.0], [69.5, 253.0], [69.6, 254.0], [69.7, 254.0], [69.8, 254.0], [69.9, 254.0], [70.0, 256.0], [70.1, 256.0], [70.2, 256.0], [70.3, 256.0], [70.4, 257.0], [70.5, 257.0], [70.6, 257.0], [70.7, 257.0], [70.8, 257.0], [70.9, 260.0], [71.0, 260.0], [71.1, 260.0], [71.2, 260.0], [71.3, 261.0], [71.4, 261.0], [71.5, 261.0], [71.6, 261.0], [71.7, 262.0], [71.8, 262.0], [71.9, 262.0], [72.0, 262.0], [72.1, 263.0], [72.2, 263.0], [72.3, 263.0], [72.4, 263.0], [72.5, 264.0], [72.6, 264.0], [72.7, 264.0], [72.8, 264.0], [72.9, 265.0], [73.0, 265.0], [73.1, 265.0], [73.2, 265.0], [73.3, 265.0], [73.4, 265.0], [73.5, 265.0], [73.6, 265.0], [73.7, 266.0], [73.8, 266.0], [73.9, 266.0], [74.0, 266.0], [74.1, 268.0], [74.2, 268.0], [74.3, 268.0], [74.4, 268.0], [74.5, 268.0], [74.6, 268.0], [74.7, 268.0], [74.8, 268.0], [74.9, 269.0], [75.0, 269.0], [75.1, 269.0], [75.2, 269.0], [75.3, 271.0], [75.4, 271.0], [75.5, 271.0], [75.6, 271.0], [75.7, 273.0], [75.8, 273.0], [75.9, 273.0], [76.0, 273.0], [76.1, 273.0], [76.2, 273.0], [76.3, 273.0], [76.4, 273.0], [76.5, 274.0], [76.6, 274.0], [76.7, 274.0], [76.8, 274.0], [76.9, 275.0], [77.0, 275.0], [77.1, 275.0], [77.2, 275.0], [77.3, 277.0], [77.4, 277.0], [77.5, 277.0], [77.6, 277.0], [77.7, 280.0], [77.8, 280.0], [77.9, 280.0], [78.0, 280.0], [78.1, 280.0], [78.2, 280.0], [78.3, 280.0], [78.4, 280.0], [78.5, 282.0], [78.6, 282.0], [78.7, 282.0], [78.8, 282.0], [78.9, 285.0], [79.0, 285.0], [79.1, 285.0], [79.2, 285.0], [79.3, 287.0], [79.4, 287.0], [79.5, 287.0], [79.6, 287.0], [79.7, 303.0], [79.8, 303.0], [79.9, 303.0], [80.0, 303.0], [80.1, 311.0], [80.2, 311.0], [80.3, 311.0], [80.4, 311.0], [80.5, 312.0], [80.6, 312.0], [80.7, 312.0], [80.8, 312.0], [80.9, 319.0], [81.0, 319.0], [81.1, 319.0], [81.2, 319.0], [81.3, 324.0], [81.4, 324.0], [81.5, 324.0], [81.6, 324.0], [81.7, 326.0], [81.8, 326.0], [81.9, 326.0], [82.0, 326.0], [82.1, 328.0], [82.2, 328.0], [82.3, 328.0], [82.4, 328.0], [82.5, 335.0], [82.6, 335.0], [82.7, 335.0], [82.8, 335.0], [82.9, 335.0], [83.0, 335.0], [83.1, 335.0], [83.2, 335.0], [83.3, 336.0], [83.4, 336.0], [83.5, 336.0], [83.6, 336.0], [83.7, 338.0], [83.8, 338.0], [83.9, 338.0], [84.0, 338.0], [84.1, 343.0], [84.2, 343.0], [84.3, 343.0], [84.4, 343.0], [84.5, 346.0], [84.6, 346.0], [84.7, 346.0], [84.8, 346.0], [84.9, 354.0], [85.0, 354.0], [85.1, 354.0], [85.2, 354.0], [85.3, 356.0], [85.4, 356.0], [85.5, 356.0], [85.6, 356.0], [85.7, 362.0], [85.8, 362.0], [85.9, 362.0], [86.0, 362.0], [86.1, 374.0], [86.2, 374.0], [86.3, 374.0], [86.4, 374.0], [86.5, 375.0], [86.6, 375.0], [86.7, 375.0], [86.8, 375.0], [86.9, 377.0], [87.0, 377.0], [87.1, 377.0], [87.2, 377.0], [87.3, 380.0], [87.4, 380.0], [87.5, 380.0], [87.6, 380.0], [87.7, 384.0], [87.8, 384.0], [87.9, 384.0], [88.0, 384.0], [88.1, 385.0], [88.2, 385.0], [88.3, 385.0], [88.4, 385.0], [88.5, 392.0], [88.6, 392.0], [88.7, 392.0], [88.8, 392.0], [88.9, 401.0], [89.0, 401.0], [89.1, 401.0], [89.2, 401.0], [89.3, 401.0], [89.4, 401.0], [89.5, 401.0], [89.6, 401.0], [89.7, 403.0], [89.8, 403.0], [89.9, 403.0], [90.0, 403.0], [90.1, 404.0], [90.2, 404.0], [90.3, 404.0], [90.4, 404.0], [90.5, 415.0], [90.6, 415.0], [90.7, 415.0], [90.8, 415.0], [90.9, 416.0], [91.0, 416.0], [91.1, 416.0], [91.2, 416.0], [91.3, 430.0], [91.4, 430.0], [91.5, 430.0], [91.6, 430.0], [91.7, 437.0], [91.8, 437.0], [91.9, 437.0], [92.0, 437.0], [92.1, 447.0], [92.2, 447.0], [92.3, 447.0], [92.4, 447.0], [92.5, 461.0], [92.6, 461.0], [92.7, 461.0], [92.8, 461.0], [92.9, 462.0], [93.0, 462.0], [93.1, 462.0], [93.2, 462.0], [93.3, 465.0], [93.4, 465.0], [93.5, 465.0], [93.6, 465.0], [93.7, 467.0], [93.8, 467.0], [93.9, 467.0], [94.0, 467.0], [94.1, 468.0], [94.2, 468.0], [94.3, 468.0], [94.4, 468.0], [94.5, 473.0], [94.6, 473.0], [94.7, 473.0], [94.8, 473.0], [94.9, 517.0], [95.0, 517.0], [95.1, 517.0], [95.2, 517.0], [95.3, 524.0], [95.4, 524.0], [95.5, 524.0], [95.6, 524.0], [95.7, 534.0], [95.8, 534.0], [95.9, 534.0], [96.0, 534.0], [96.1, 546.0], [96.2, 546.0], [96.3, 546.0], [96.4, 546.0], [96.5, 548.0], [96.6, 548.0], [96.7, 548.0], [96.8, 548.0], [96.9, 561.0], [97.0, 561.0], [97.1, 561.0], [97.2, 561.0], [97.3, 661.0], [97.4, 661.0], [97.5, 661.0], [97.6, 661.0], [97.7, 767.0], [97.8, 767.0], [97.9, 767.0], [98.0, 767.0], [98.1, 838.0], [98.2, 838.0], [98.3, 838.0], [98.4, 838.0], [98.5, 986.0], [98.6, 986.0], [98.7, 986.0], [98.8, 986.0], [98.9, 1208.0], [99.0, 1208.0], [99.1, 1208.0], [99.2, 1208.0], [99.3, 1496.0], [99.4, 1496.0], [99.5, 1496.0], [99.6, 1496.0], [99.7, 2859.0], [99.8, 2859.0], [99.9, 2859.0], [100.0, 2859.0]], "isOverall": false, "label": "GET /dashboard", "isController": false}], "supportsControllersDiscrimination": true, "maxX": 100.0, "title": "Response Time Percentiles"}},
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
        data: {"result": {"minY": 1.0, "minX": 0.0, "maxY": 90.0, "series": [{"data": [[0.0, 31.0], [600.0, 1.0], [2800.0, 1.0], [700.0, 1.0], [200.0, 90.0], [800.0, 1.0], [900.0, 1.0], [300.0, 23.0], [1200.0, 1.0], [1400.0, 1.0], [400.0, 15.0], [100.0, 78.0], [500.0, 6.0]], "isOverall": false, "label": "GET /dashboard", "isController": false}], "supportsControllersDiscrimination": true, "granularity": 100, "maxX": 2800.0, "title": "Response Time Distribution"}},
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
        data: {"result": {"minY": 1.0, "minX": 0.0, "ticks": [[0, "Requests having \nresponse time <= 500ms"], [1, "Requests having \nresponse time > 500ms and <= 1,500ms"], [2, "Requests having \nresponse time > 1,500ms"], [3, "Requests in error"]], "maxY": 237.0, "series": [{"data": [[0.0, 237.0]], "color": "#9ACD32", "isOverall": false, "label": "Requests having \nresponse time <= 500ms", "isController": false}, {"data": [[1.0, 12.0]], "color": "yellow", "isOverall": false, "label": "Requests having \nresponse time > 500ms and <= 1,500ms", "isController": false}, {"data": [[2.0, 1.0]], "color": "orange", "isOverall": false, "label": "Requests having \nresponse time > 1,500ms", "isController": false}, {"data": [], "color": "#FF6347", "isOverall": false, "label": "Requests in error", "isController": false}], "supportsControllersDiscrimination": false, "maxX": 2.0, "title": "Synthetic Response Times Distribution"}},
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
        data: {"result": {"minY": 6.668, "minX": 1.78160112E12, "maxY": 6.668, "series": [{"data": [[1.78160112E12, 6.668]], "isOverall": false, "label": "Dashboard Thread Group", "isController": false}], "supportsControllersDiscrimination": false, "granularity": 60000, "maxX": 1.78160112E12, "title": "Active Threads Over Time"}},
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
        data: {"result": {"minY": 205.88888888888889, "minX": 1.0, "maxY": 349.5, "series": [{"data": [[8.0, 221.2], [4.0, 245.8], [2.0, 236.66666666666666], [1.0, 349.5], [9.0, 273.38461538461536], [10.0, 205.88888888888889], [5.0, 212.56666666666666], [11.0, 276.0], [12.0, 222.0], [6.0, 271.70129870129887], [3.0, 255.33333333333331], [7.0, 230.04166666666669]], "isOverall": false, "label": "GET /dashboard", "isController": false}, {"data": [[6.668, 243.63999999999987]], "isOverall": false, "label": "GET /dashboard-Aggregated", "isController": false}], "supportsControllersDiscrimination": true, "maxX": 12.0, "title": "Time VS Threads"}},
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
        data : {"result": {"minY": 658.3333333333334, "minX": 1.78160112E12, "maxY": 334541.6666666667, "series": [{"data": [[1.78160112E12, 334541.6666666667]], "isOverall": false, "label": "Bytes received per second", "isController": false}, {"data": [[1.78160112E12, 658.3333333333334]], "isOverall": false, "label": "Bytes sent per second", "isController": false}], "supportsControllersDiscrimination": false, "granularity": 60000, "maxX": 1.78160112E12, "title": "Bytes Throughput Over Time"}},
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
        data: {"result": {"minY": 243.63999999999987, "minX": 1.78160112E12, "maxY": 243.63999999999987, "series": [{"data": [[1.78160112E12, 243.63999999999987]], "isOverall": false, "label": "GET /dashboard", "isController": false}], "supportsControllersDiscrimination": true, "granularity": 60000, "maxX": 1.78160112E12, "title": "Response Time Over Time"}},
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
        data: {"result": {"minY": 92.24400000000001, "minX": 1.78160112E12, "maxY": 92.24400000000001, "series": [{"data": [[1.78160112E12, 92.24400000000001]], "isOverall": false, "label": "GET /dashboard", "isController": false}], "supportsControllersDiscrimination": true, "granularity": 60000, "maxX": 1.78160112E12, "title": "Latencies Over Time"}},
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
        data: {"result": {"minY": 31.811999999999994, "minX": 1.78160112E12, "maxY": 31.811999999999994, "series": [{"data": [[1.78160112E12, 31.811999999999994]], "isOverall": false, "label": "GET /dashboard", "isController": false}], "supportsControllersDiscrimination": true, "granularity": 60000, "maxX": 1.78160112E12, "title": "Connect Time Over Time"}},
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
        data: {"result": {"minY": 52.0, "minX": 1.78160112E12, "maxY": 2859.0, "series": [{"data": [[1.78160112E12, 2859.0]], "isOverall": false, "label": "Max", "isController": false}, {"data": [[1.78160112E12, 52.0]], "isOverall": false, "label": "Min", "isController": false}, {"data": [[1.78160112E12, 403.9]], "isOverall": false, "label": "90th percentile", "isController": false}, {"data": [[1.78160112E12, 1349.1200000000026]], "isOverall": false, "label": "99th percentile", "isController": false}, {"data": [[1.78160112E12, 209.0]], "isOverall": false, "label": "Median", "isController": false}, {"data": [[1.78160112E12, 520.1499999999999]], "isOverall": false, "label": "95th percentile", "isController": false}], "supportsControllersDiscrimination": false, "granularity": 60000, "maxX": 1.78160112E12, "title": "Response Time Percentiles Over Time (successful requests only)"}},
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
    data: {"result": {"minY": 151.0, "minX": 6.0, "maxY": 292.5, "series": [{"data": [[33.0, 205.0], [20.0, 204.5], [24.0, 217.0], [6.0, 292.5], [25.0, 224.0], [27.0, 151.0], [30.0, 207.0]], "isOverall": false, "label": "Successes", "isController": false}], "supportsControllersDiscrimination": false, "granularity": 1000, "maxX": 33.0, "title": "Response Time Vs Request"}},
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
    data: {"result": {"minY": 51.0, "minX": 6.0, "maxY": 63.0, "series": [{"data": [[33.0, 52.0], [20.0, 61.0], [24.0, 51.0], [6.0, 63.0], [25.0, 61.0], [27.0, 52.0], [30.0, 61.0]], "isOverall": false, "label": "Successes", "isController": false}], "supportsControllersDiscrimination": false, "granularity": 1000, "maxX": 33.0, "title": "Latencies Vs Request"}},
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
        data: {"result": {"minY": 4.166666666666667, "minX": 1.78160112E12, "maxY": 4.166666666666667, "series": [{"data": [[1.78160112E12, 4.166666666666667]], "isOverall": false, "label": "hitsPerSecond", "isController": false}], "supportsControllersDiscrimination": false, "granularity": 60000, "maxX": 1.78160112E12, "title": "Hits Per Second"}},
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
        data: {"result": {"minY": 4.166666666666667, "minX": 1.78160112E12, "maxY": 4.166666666666667, "series": [{"data": [[1.78160112E12, 4.166666666666667]], "isOverall": false, "label": "200", "isController": false}], "supportsControllersDiscrimination": false, "granularity": 60000, "maxX": 1.78160112E12, "title": "Codes Per Second"}},
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
        data: {"result": {"minY": 4.166666666666667, "minX": 1.78160112E12, "maxY": 4.166666666666667, "series": [{"data": [[1.78160112E12, 4.166666666666667]], "isOverall": false, "label": "GET /dashboard-success", "isController": false}], "supportsControllersDiscrimination": true, "granularity": 60000, "maxX": 1.78160112E12, "title": "Transactions Per Second"}},
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
        data: {"result": {"minY": 4.166666666666667, "minX": 1.78160112E12, "maxY": 4.166666666666667, "series": [{"data": [[1.78160112E12, 4.166666666666667]], "isOverall": false, "label": "Transaction-success", "isController": false}, {"data": [], "isOverall": false, "label": "Transaction-failure", "isController": false}], "supportsControllersDiscrimination": true, "granularity": 60000, "maxX": 1.78160112E12, "title": "Total Transactions Per Second"}},
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

