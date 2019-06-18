# -*- coding: utf-8 -*-
import scrapy


class DcsProductSpider(scrapy.Spider):
    name = 'new_product'
    allowed_domains = ['dcs.dk']
    start_urls = ['https://dev.dcs.dk/da/sticker/8?page=1&tb%5Blimit%5D=96&tb%5Bview%5D=grid&tb%5Bsort%5D=popularity_desc&tb%5Bstock%5D=instock']

    def parse(self, response):   
        urls = response.xpath('//a[contains(@href, "/da/p/")]/@href').extract()
        for url in urls:
            url = response.urljoin(url)
            yield scrapy.Request(url=url, callback=self.parse_details)   

    def parse_details(self, response):
#       accessories=response.css('div.accessories > div > div > div > div > h6 > a::attr(href)').extract()
#       for accessoriess in accessories:
          yield {
             'short_description1':response.css('div.product-info > div > div.description > span::text').extract_first().strip(),
             'short_description2':response.css('div.product-info > div > div.description > span > p *::text').extract(),
             'long_description':response.css('div.product-info > div > div.more-info > div::text').extract()[1].strip(),
             'reference_number':response.css('div.product-info > div > div.more-info > div.info > ul > li > p > i[itemprop="sku"]::text').extract(),
             'categories':";".join(response.css('ol.breadcrumb > li > a::text').extract()),
#             'category':response.css('ol.breadcrumb > li > a::text').extract()[1].strip(),
#             'subcategory1':response.css('ol.breadcrumb > li > a::text').extract()[2].strip(),
#             'subcategory2':response.css('ol.breadcrumb > li > a::text').extract()[3].strip(),
             'image_url':";".join(response.css('div.product-lightbox-carousel > ul > li *::attr(src)').extract()),
             'specification':";;".join(response.css('table[class="table table-specs table-striped"] > tbody > tr > td::text').extract()),
             'detail_specification':";;".join(response.css('table.table-specsextended > tbody > tr > td').extract()),
             'toner_relation':";".join(response.css('div.toner-relations > div.product-line > div.name > ul > li > i[itemprop="sku"]::text').extract()),
             'features':response.css('div[class="ccs-ds-textFeatures"]').extract(),
 #            'ref_accessories':accessoriess.split('-')[-1],
             }
