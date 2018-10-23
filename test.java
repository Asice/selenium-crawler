package com.bode.common.repair;

import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersAdapter;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.bode.common.utils.HttpClientUtil;
import com.bode.newspaper.mapper.Shopee_keywordMapper;
import com.bode.newspaper.mapper.Shopee_keyword_itmeMapper;
import com.bode.newspaper.model.Shopee_keyword_itme;

import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import net.lightbody.bmp.BrowserMobProxy;
import net.lightbody.bmp.BrowserMobProxyServer;
import net.lightbody.bmp.client.ClientUtil;
import net.lightbody.bmp.proxy.CaptureType;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
/**
 * 获取销售数量,chromeDriver 加上301重定向
 *
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ShopeeProductSuggestSold {

	
	@Autowired
	private Shopee_keywordMapper shopee_keywordMapper;
	@Autowired
	private Shopee_keyword_itmeMapper shopee_keyword_itmeMapper;
	
	
	@Test
	public void _test() throws Exception {
     //设置环境变量
		System.setProperty("webdriver.chrome.driver", "C:\\Users\\JD\\Documents\\google\\chromedriver.exe");
		String signature="/api/v2/item/get";
		String item_url="https://shopee.com.my/x-i.4156284.730663574";
		
		
		//需要处理的队列，线程安全
		ConcurrentLinkedQueue<Shopee_keyword_itme> queue=shopee_keyword_itmeMapper.getAllSold();
		
		System.out.println(queue.size());
		for(int i=0;i<5;i++) {
			Thread thread=new Thread(new Runnable() {
				@Override
				public void run() {
        //多线程执行
					chromeGetJsonResponse(queue,item_url, signature);
				}
			});
			thread.start();
		}
		//chromeGetJsonResponse(queue,search_url, signature, imgurl, div, site, now);
		while(true) {
			Thread.sleep(1000);
			if(queue.size()==0) {
				Thread.sleep(20*1000);
				break;
			}
		}
		
	}
  //数据结果处理
	private void shopeeMessageSave(String content) {
		
		
		System.out.println("jsonss_content:"+content);
		try {
			JSONObject obj=JSONObject.fromObject(content);
			if(obj.containsKey("item")) {
				JSONObject item=obj.getJSONObject("item");
				Integer sold=null,sold_max=null,sold_all=null;
				if(null!=item.get("sold")&&!item.get("sold").equals("null")) {
					sold=item.getInt("sold");
				}
				if(item.containsKey("models")) {
					JSONArray arr=item.getJSONArray("models");
					if(arr.size()==0) {
						sold_max=sold;
						sold_all=sold;
						
					}else {
						Iterator<JSONObject> it=arr.iterator();
						while(it.hasNext()) {
							JSONObject model=it.next();
							if(model.containsKey("sold")) {
								if(null!=model.get("sold")&&!model.get("sold").equals("null")) {
									if(null==sold_all)sold_all=0;
									if(null==sold_max)sold_max=0;
									int sd=model.getInt("sold");
									sold_all+=sd;
									if(sd>sold_max){
										sold_max=sd;
									}
								}
							}
						}
					}
					
				}
				if(sold!=null&&sold_max!=null&&sold_all!=null) {
					shopee_keyword_itmeMapper.updateSold(sold,sold_max,sold_all,new Date(item.getLong("ctime")*1000), item.getInt("itemid"));
				}
			}
		}catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private static  String doGet(String url){
		CloseableHttpClient httpclient = HttpClientUtil.getConnection();
		HttpGet httpGet = new HttpGet(url);// 创建httpPost  
        CloseableHttpResponse response = null;
        try {
        	response = httpclient.execute(httpGet);
            StatusLine status = response.getStatusLine();
            int state = status.getStatusCode();
            if (state == HttpStatus.SC_OK) {
            	HttpEntity responseEntity = response.getEntity();
            	String jsonString = EntityUtils.toString(responseEntity);
            	return jsonString;
            }else{
            	HttpEntity responseEntity = response.getEntity();
            	httpGet.abort();//主动关闭链接
            	String jsonString = EntityUtils.toString(responseEntity);
            	System.out.println("请求返回:"+state+"("+jsonString+")");
            	return null;
				
			}
        }catch(Exception e) {
        	System.out.println("虾皮CloseableHttpClient调用错误"+e);
        }
        finally {
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
	}
	

	private  void chromeGetJsonResponse(ConcurrentLinkedQueue<Shopee_keyword_itme> queue,String itme_url,String signature) {
		ChromeDriver driver = null;
		try{
			// start the proxy
			BrowserMobProxy proxy = new BrowserMobProxyServer();
			proxy.start((int)Thread.currentThread().getId());
			// get the Selenium proxy object
			Proxy seleniumProxy = ClientUtil.createSeleniumProxy(proxy);
			ChromeOptions options = new ChromeOptions();
			// options.addArguments("--headless");
			// add whatever extensions you need
			// for example I needed one of adding proxy, and one for blocking
			// images
			// options.addExtensions(new File(file, "proxy.zip"));
			// options.addExtensions(new File("extensions",
			// "Block-image_v1.1.crx"));
			// configure it as a desired capability
	
			DesiredCapabilities cap = DesiredCapabilities.chrome();
			
			cap.setCapability(ChromeOptions.CAPABILITY, options);
			// 代理
			cap.setCapability(CapabilityType.PROXY, seleniumProxy);
	
			// enable more detailed HAR capture, if desired (see CaptureType for the
			// complete list)
			proxy.enableHarCaptureTypes(CaptureType.RESPONSE_CONTENT);
			
			final AtomicInteger filterHitCount = new AtomicInteger(0);
			
			proxy.addFirstHttpFilterFactory(new HttpFiltersSourceAdapter() {
	            @Override
	            public HttpFilters filterRequest(HttpRequest originalRequest) {
	                return new HttpFiltersAdapter(originalRequest) {
	                    @Override
	                    public HttpObject proxyToClientResponse(HttpObject httpObject) {
	                    	String requestUrl=originalRequest.uri();
	    		            if(requestUrl.contains(signature)) {
	    		            	if(httpObject instanceof HttpResponse){
	    		            		HttpResponse response=(HttpResponse)httpObject;
	    		            		response.setStatus(HttpResponseStatus.FOUND);
	    		            		//循环302
	    		            		if(queue.size()>0) {
	    		            			filterHitCount.incrementAndGet();
    		        					Shopee_keyword_itme bean=queue.poll();
    		        					response.headers().add("location", signature+"?itemid="+bean.getItemid()+"&shopid="+bean.getShopid());
    		        					try {
											Thread.sleep(100);
										} catch (InterruptedException e) {
											e.printStackTrace();
										}
	    		            		}
	    		            	}
	    		            }
	                        return super.proxyToClientResponse(httpObject);
	                    }
	                };
	            }

	            @Override
	            public int getMaximumResponseBufferSizeInBytes() {
	                return 10000;
	            }
	        });  
			
	      proxy.addResponseFilter((response, contents, messageInfo) -> {
	        	String requestUrl=messageInfo.getUrl();
	            if(requestUrl.contains(signature)) {
	            	///api/v2/item/get?itemid=319675&shopid=33094
	            	//获取爬虫信息
					shopeeMessageSave(contents.getTextContents());
	            	//proxy.abort();
	            }
	        });
			
			driver = new ChromeDriver(cap);
			// navigate to the page
			//driver.navigate().to("https://shopee.com.my/");
			driver.navigate().to(itme_url);
			while(queue.size()>0) {
				int count=filterHitCount.incrementAndGet();
    			if(count>20) {
    				driver.navigate().to(itme_url);
    			}
				try {
					Thread.sleep(600);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			/*while(true) {
				if(queue.size()>0&&!responseFilterFired.get()) {
					Shopee_keyword_itme bean=queue.poll();
					responseFilterFired.set(true);
					driver.navigate().to(itme_url+"."+bean.getShopid()+"."+bean.getItemid());
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				if(queue.size()==0)break;
			}*/
			System.out.println("全部线程处理完成");
		}finally {
			if(driver!=null) {
				driver.quit();
			}
		}
	}

}
