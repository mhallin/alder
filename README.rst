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
* Grunt_

.. code-block:: sh

   $ grunt
   $ lein ring server-headless

   # Visit http://localhost:3000


Development Setup
-----------------

You can use Grunt's auto watch feature and lein-figwheel_ to auto
reload and push code changes to the browser for a very interactive
development environment.

.. code-block:: sh

   # In one terminal window
   $ grunt watch

   # In another window
   $ lein figwheel

   # Open http://localhost:3449

Deployment Setup
----------------

It's convenient to package the entire application up in a JAR file,
through Clojure and Ring's Uberjar feature.

.. code-block:: sh

   $ grunt
   $ lein ring uberjar

.. _Clojurescript: https://github.com/clojure/clojurescript
.. _Leiningen: http://leiningen.org
.. _Node.js: http://nodejs.org
.. _npm: http://npmjs.com
.. _Grunt: http://gruntjs.com
.. _lein-figwheel: https://github.com/bhauman/lein-figwheel
