# selenium-crawler
1.通过selenium和chrome driver 驱动浏览器访问目标网站\n
2.通过BrowserMobProxy代理拦截需要的数据（如json），然后改变Response header 302 重定向和location 来实现内部爬虫链的循环。
3.不需要每个目标地址都要url访问，都是通过重定向获取需要的数据，速度就和CloseableHttpClient差不多，用于解决加密链接和反爬虫比较难处理的。
4.TODO 因为浏览器有重定向次数限制，所有几次（30？）重定向过后需要重新刷新页面，目前的耗时基本在这里
