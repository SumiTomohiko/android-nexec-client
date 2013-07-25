
nexec client for Android
************************

.. image:: icon.png

.. contents:: Table of contents

Overview
========

nexec client for Android is a service application of nexec_ client. This service
strengthens you to use applications on a FreeBSD machine from your Android
tablet.

.. _nexec: http://neko-daisuki.ddo.jp/~SumiTomohiko/nexec/index.html

This service has the following two additional features which have not been
included nexec_ (yet).

* Access control mechanism
* Redirection of file access

One is the access control mechanism. This service denies requests to open
unexpected files.

The other is redirection of file access. If an application in a master machine
requested to open a file, this service can open another file instead of the
requested one.

Google play
===========

This application is available at `Google play`_.

.. _Google play: https://play.google.com/store/apps/details?id=jp.gr.java_conf.neko_daisuki.android.nexec.client

How to use
==========

This is not a launcher application but a service.
-------------------------------------------------

This is not a launcher application. You can use this with other applications
such as `android-nexec-client-demo`_ or `animator`_.

The `android-nexec-client-demo`_ page prepares `a tutorial section`_. This is a
good place to start nexec_.

.. _android-nexec-client-demo:
    http://neko-daisuki.ddo.jp/~SumiTomohiko/android-nexec-client-demo/index.html
.. _animator: http://neko-daisuki.ddo.jp/~SumiTomohiko/animator/index.html
.. _a tutorial section:
    http://neko-daisuki.ddo.jp/~SumiTomohiko/android-nexec-client-demo/index.html#tutorial


If your application had requested to nexec, this service shows the following
screen to show you what the application had requested. When you dislike these
settings, you can cancel. Of cource, if you want to accept, please push "Okey".
This service will connect with the server to run the command.

Host page
---------

This page shows you the nexec server information.

.. image:: host_page-thumb.png
    :target: host_page.png

Command page
------------

You can see what command will be executed in this page.

.. image:: command_page-thumb.png
    :target: command_page.png

Permission page
---------------

Files in this page are what the application requested to open/read/write.

.. image:: permission_page-thumb.png
    :target: permission_page.png

Redirection page
----------------

"Redirection" page shows sources/destination of redirection.

.. image:: redirection_page-thumb.png
    :target: redirection_page.png

Anything else
=============

License
-------

nexec client for Android is under `the MIT license`_.

.. _the MIT license:
    https://github.com/SumiTomohiko/android-nexec-client/blob/master/COPYING.rst#mit-license

GitHub repository
-----------------

GitHub repository of this is
https://github.com/SumiTomohiko/android-nexec-client.

Author
------

The author of this is `Tomohiko Sumi`_.

.. _Tomohiko Sumi: http://neko-daisuki.ddo.jp/~SumiTomohiko/index.html

.. vim: tabstop=4 shiftwidth=4 expandtab softtabstop=4
