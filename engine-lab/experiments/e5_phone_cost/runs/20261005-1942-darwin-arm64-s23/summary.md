# E5 — 폰 비용

## 뜨는 시간과 메모리

| 설정 | 뜨는 시간 | RSS(뜬 직후) | RSS(일한 뒤) | 최대 RSS | adb 왕복 |
| --- | ---: | ---: | ---: | ---: | ---: |
| main-t1 | 1.4초 | 507MB | 516MB | 517MB | 1.9ms |
| human-t1 | 2.5초 | 1003MB | 1015MB | 1016MB | 1.9ms |
| human-t2 | 3.0초 | 1009MB | 1037MB | 1039MB | 1.6ms |
| human-t4 | 3.6초 | 1021MB | 1072MB | 1075MB | 1.8ms |
| human-t1-buf13 | 3.9초 | 1001MB | 1009MB | 1010MB | 0.9ms |
| human-t4-buf13 | 3.9초 | 1012MB | 1037MB | 1038MB | 1.3ms |
| gobot-t1 | 3.9초 | 1003MB | 1015MB | 1016MB | 1.1ms |
| gobot-t4 | 4.2초 | 1021MB | 1062MB | 1065MB | 1.1ms |
| analysis-main | 2.2초 | 516MB | 547MB | 550MB | –ms |
| analysis-human | 4.7초 | 1021MB | 1065MB | 1068MB | –ms |
| analysis-main+gtp | 2.5초 | 516MB (+ 옆의 GTP 508MB) | 549MB | 553MB | –ms |
| analysis-human+gtp | 4.7초 | 1020MB (+ 옆의 GTP 508MB) | 1068MB | 1071MB | –ms |

## 한 번에 드는 시간(중앙값, ms)

| 설정 | 판 | 무엇 | 시간 |
| --- | ---: | --- | ---: |
| main-t1 | 13 | raw-nn | 265.9 |
| main-t1 | 13 | search-16 | 4329.8 |
| main-t1 | 13 | search-32 | 8806.6 |
| main-t1 | 13 | search-40 | 11178.4 |
| main-t1 | 19 | raw-nn | 494.5 |
| main-t1 | 19 | search-16 | 7688.5 |
| main-t1 | 19 | search-32 | 15401.6 |
| main-t1 | 19 | search-40 | 19366.5 |
| human-t1 | 13 | raw-nn | 285.3 |
| human-t1 | 13 | raw-human-nn | 288.3 |
| human-t1 | 13 | search-16 | 4871.1 |
| human-t1 | 13 | search-32 | 9443.9 |
| human-t1 | 13 | search-40 | 11742.9 |
| human-t1 | 19 | raw-nn | 492.2 |
| human-t1 | 19 | raw-human-nn | 498.5 |
| human-t1 | 19 | search-16 | 8268.8 |
| human-t1 | 19 | search-32 | 16320.8 |
| human-t1 | 19 | search-40 | 21463.6 |
| human-t2 | 13 | raw-nn | 303.0 |
| human-t2 | 13 | raw-human-nn | 302.3 |
| human-t2 | 13 | search-16 | 3753.3 |
| human-t2 | 13 | search-32 | 7081.1 |
| human-t2 | 13 | search-40 | 9996.2 |
| human-t2 | 19 | raw-nn | 670.8 |
| human-t2 | 19 | raw-human-nn | 670.0 |
| human-t2 | 19 | search-16 | 8431.8 |
| human-t2 | 19 | search-32 | 14891.3 |
| human-t2 | 19 | search-40 | 18888.8 |
| human-t4 | 13 | raw-nn | 398.1 |
| human-t4 | 13 | raw-human-nn | 372.1 |
| human-t4 | 13 | search-16 | 4589.9 |
| human-t4 | 13 | search-32 | 6257.4 |
| human-t4 | 13 | search-40 | 8151.5 |
| human-t4 | 19 | raw-nn | 748.7 |
| human-t4 | 19 | raw-human-nn | 747.9 |
| human-t4 | 19 | search-16 | 9224.0 |
| human-t4 | 19 | search-32 | 11764.0 |
| human-t4 | 19 | search-40 | 14049.0 |
| human-t1-buf13 | 13 | raw-nn | 408.1 |
| human-t1-buf13 | 13 | raw-human-nn | 412.2 |
| human-t1-buf13 | 13 | search-16 | 6851.7 |
| human-t1-buf13 | 13 | search-32 | 13973.0 |
| human-t1-buf13 | 13 | search-40 | 17671.1 |
| human-t4-buf13 | 13 | raw-nn | 423.5 |
| human-t4-buf13 | 13 | raw-human-nn | 406.8 |
| human-t4-buf13 | 13 | search-16 | 4828.2 |
| human-t4-buf13 | 13 | search-32 | 6258.8 |
| human-t4-buf13 | 13 | search-40 | 8375.7 |
| gobot-t1 | 13 | gobot-genmove | 18391.1 |
| gobot-t1 | 19 | gobot-genmove | 32006.0 |
| gobot-t4 | 13 | gobot-genmove | 9540.2 |
| gobot-t4 | 19 | gobot-genmove | 17135.8 |
| analysis-main | 13 | json-1visit | 551.3 |
| analysis-main | 13 | json-ownership-16 | 5808.9 |
| analysis-main | 13 | json-ownership-64 | 13480.3 |
| analysis-main | 13 | json-ownership-128 | 28621.8 |
| analysis-main | 19 | json-1visit | 843.2 |
| analysis-main | 19 | json-ownership-16 | 9622.2 |
| analysis-main | 19 | json-ownership-64 | 19579.7 |
| analysis-main | 19 | json-ownership-128 | 42136.9 |
| analysis-human | 13 | json-1visit | 1082.6 |
| analysis-human | 13 | json-ownership-16 | 5740.8 |
| analysis-human | 13 | json-ownership-64 | 15082.2 |
| analysis-human | 13 | json-ownership-128 | 31093.4 |
| analysis-human | 19 | json-1visit | 1734.6 |
| analysis-human | 19 | json-ownership-16 | 9435.4 |
| analysis-human | 19 | json-ownership-64 | 21861.4 |
| analysis-human | 19 | json-ownership-128 | 42094.6 |
| analysis-main+gtp | 13 | json-1visit | 554.3 |
| analysis-main+gtp | 13 | json-ownership-16 | 6068.9 |
| analysis-main+gtp | 13 | json-ownership-64 | 14646.3 |
| analysis-main+gtp | 13 | json-ownership-128 | 31091.3 |
| analysis-main+gtp | 19 | json-1visit | 865.0 |
| analysis-main+gtp | 19 | json-ownership-16 | 10358.1 |
| analysis-main+gtp | 19 | json-ownership-64 | 24331.3 |
| analysis-main+gtp | 19 | json-ownership-128 | 42112.7 |
| analysis-human+gtp | 13 | json-1visit | 1061.1 |
| analysis-human+gtp | 13 | json-ownership-16 | 6015.5 |
| analysis-human+gtp | 13 | json-ownership-64 | 14765.5 |
| analysis-human+gtp | 13 | json-ownership-128 | 29701.5 |
| analysis-human+gtp | 19 | json-1visit | 1716.5 |
| analysis-human+gtp | 19 | json-ownership-16 | 9533.2 |
| analysis-human+gtp | 19 | json-ownership-64 | 23594.8 |
| analysis-human+gtp | 19 | json-ownership-128 | 43454.1 |
