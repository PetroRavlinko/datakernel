---
id: ot
filename: ot
title: OT Module
prev: cloud/fs.html
next: cloud/aggregation.html
nav-menu: cloud
layout: cloud

---

OT was designed to create applications for collaborative editing based on principles, which conceptually resemble 
Git but with support of arbitrary user-defined data types and automatic conflict resolution using operational 
transformations (OT). Unlike traditional databases, data is represented as commit graph. This approach revolutionizes 
and provides truly distributed and asynchronous online/offline data processing.

You can add OT module to your project by inserting dependency in `pom.xml`: 

{% highlight xml %}
<dependency>
    <groupId>io.datakernel</groupId>
    <artifactId>datakernel-ot</artifactId>
    <version>{{site_datakernel_version}}</version>
</dependency>
{% endhighlight %}

### The core features of OT are:
* Organizing data as commit graph.
* Efficient algorithms for managing graph of commits and automatic conflict resolution while merging.
* Ability to save/load snapshots and create backups.
* Working with arbitrary user-defined data structures.
* State manager allows to monitor current state of OT system.

Note that OT is suitable for private cloud solutions and is **not** optimised for working with non-trusted servers. 
If you are looking for a global internet-wide cloud storage solution, please consider [Global-OT module](/docs/global-cloud/global-ot.html).

## This module on [GitHub repository](https://github.com/softindex/datakernel/tree/master/cloud-ot)