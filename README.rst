===========
 Alder DSP
===========

Graph node editor to assemble Web Audio API nodes. Written primarily
in Clojurescript_.


Installation
============

Requirements:

* Leiningen_
* Node.js_ and npm_
* Gulp_

.. code-block:: sh

   $ ./node-modules/.bin/gulp
   $ DATABASE_URL="jdbc:postgresql://localhost/alder" lein run

   # Visit http://localhost:3449


Development Setup
-----------------

You can use Gulp's file watch feature and lein-figwheel_ to auto
reload and push code changes to the browser for a very interactive
development environment.

.. code-block:: sh

   # In one terminal window
   $ ./node-modules/.bin/gulp watch

   # In another window
   $ DATABASE_URL="jdbc:postgresql://localhost/alder" lein figwheel

   # Visit http://localhost:3449

Deployment Setup
----------------

It's convenient to package the entire application up in a JAR file,
through Clojure and Ring's Uberjar feature.

.. code-block:: sh

   $ ./node-modules/.bin/gulp
   $ lein ring uberjar

.. _Clojurescript: https://github.com/clojure/clojurescript
.. _Leiningen: http://leiningen.org
.. _Node.js: http://nodejs.org
.. _npm: http://npmjs.com
.. _Gulp: http://gulpjs.com
.. _lein-figwheel: https://github.com/bhauman/lein-figwheel
