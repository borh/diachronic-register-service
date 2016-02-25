FROM adzerk/boot-clj
MAINTAINER Bor Hodošček <bor@lang.osaka-u.ac.jp>

# RUN apt-get install mecab unidic-mecab mecab-utils libmecab-jni libmecab-java
RUN apt-get install -y mecab mecab-utils libmecab-jni libmecab-java

RUN wget "http://en.sourceforge.jp/frs/redir.php\\?m\\=jaist\\&f\\=%2Funidic%2F58338%2Funidic-mecab-2.1.2_src.zip"
RUN unzip -x unidic-mecab-2.1.2_src.zip
RUN cd unidic-mecab-2.1.2_src && ./configure --prefix=/usr && make -j4 && make install && cd ..
