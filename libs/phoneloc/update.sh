#!/bin/bash
make
./generateData Mps -c phoneloc.txt
cp mokee-phoneloc.dat $dirname $(dirname $(dirname $(dirname $(dirname $(pwd)))))/vendor/mk/prebuilt/common/media/mokee-phoneloc.dat
make clean
rm mokee-phoneloc.dat
