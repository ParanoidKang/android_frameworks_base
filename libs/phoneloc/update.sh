#!/bin/bash
make
./generateData Mps -c phoneloc.txt
cp phoneloc.dat $dirname $(dirname $(dirname $(dirname $(dirname $(pwd)))))/vendor/pa/prebuilt/common/media/mokee-phoneloc.dat
make clean
rm phoneloc.dat
