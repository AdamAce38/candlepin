/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.spec.content;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.collection;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.candlepin.dto.api.client.v1.CertificateDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.request.Request;
import org.candlepin.spec.bootstrap.client.request.Response;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Content;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.CertificateUtil;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SpecTest
public class ConditionalContentSpecTest {
    private static final String CONTENT_OID = "1.3.6.1.4.1.2312.9.2.";

    @Test
    public void shouldIncludeConditionalContentSets() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();
        ProductDTO reqProd1 = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng());
        ProductDTO reqProd2 = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng());
        ProductDTO reqProd3 = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng());
        ProductDTO bundledProd = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng()
            .providedProducts(Set.of(reqProd1, reqProd2, reqProd3)));
        adminClient.owners().createPool(ownerKey, Pools.random(bundledProd));

        // Create our dependent provided product, which carries content sets -- each of which of which
        // requires one of the provided products above
        ProductDTO dependentProvProd = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng());
        ContentDTO conditionalContent1 = adminClient.ownerContent().createContent(ownerKey, Content.random()
            .id(StringUtil.random(5, StringUtil.CHARSET_NUMERIC))
            .type("yum")
            .modifiedProductIds(Set.of(reqProd1.getId())));
        ContentDTO conditionalContent2 = adminClient.ownerContent().createContent(ownerKey, Content.random()
            .id(StringUtil.random(5, StringUtil.CHARSET_NUMERIC))
            .type("yum")
            .modifiedProductIds(Set.of(reqProd2.getId())));
        ContentDTO conditionalContent3 = adminClient.ownerContent().createContent(ownerKey, Content.random()
            .id(StringUtil.random(5, StringUtil.CHARSET_NUMERIC))
            .type("yum")
            .modifiedProductIds(Set.of(reqProd3.getId())));
        adminClient.ownerProducts()
            .addContent(ownerKey, dependentProvProd.getId(), conditionalContent1.getId(), true);
        adminClient.ownerProducts()
            .addContent(ownerKey, dependentProvProd.getId(), conditionalContent2.getId(), true);
        adminClient.ownerProducts()
            .addContent(ownerKey, dependentProvProd.getId(), conditionalContent3.getId(), true);

        ProductDTO dependentProd = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng()
            .providedProducts(Set.of(dependentProvProd)));
        adminClient.owners().createPool(ownerKey, Pools.random(dependentProd));

        // Bind to the normal subscription first
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("system.certificate_version", "1.0")));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumerClient.consumers().bindProduct(consumer.getUuid(), bundledProd.getId());

        // Bind to the dependent subscription which requires the product(s) provided by the previously
        // bound subscription:
        JsonNode ents = consumerClient.consumers().bindProduct(consumer.getUuid(), dependentProd.getId());
        String cert = getCertFromEnt(ents.get(0));
        assertThat(getContentRepoType(cert, conditionalContent1.getId()))
            .isEqualTo(conditionalContent1.getType());
        assertThat(getContentRepoType(cert, conditionalContent2.getId()))
            .isEqualTo(conditionalContent2.getType());
        assertThat(getContentRepoType(cert, conditionalContent3.getId()))
            .isEqualTo(conditionalContent3.getType());
    }

    @Test
    public void shouldIncludeConditionalContentSetsSelectively() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();
        ProductDTO reqProd1 = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng());
        ProductDTO reqProd2 = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng());
        ProductDTO reqProd3 = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng());
        ProductDTO bundledProd1 = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng()
            .providedProducts(Set.of(reqProd1, reqProd2, reqProd3)));
        adminClient.owners().createPool(ownerKey, Pools.random(bundledProd1));
        ProductDTO bundledProd2 = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng()
            .providedProducts(Set.of(reqProd1, reqProd2)));
        adminClient.owners().createPool(ownerKey, Pools.random(bundledProd2));

        // Create our dependent provided product, which carries content sets -- each of which of which
        // requires one of the provided products above
        ProductDTO dependentProvProd = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng());
        ContentDTO conditionalContent1 = adminClient.ownerContent().createContent(ownerKey, Content.random()
            .id(StringUtil.random(5, StringUtil.CHARSET_NUMERIC))
            .type("yum")
            .modifiedProductIds(Set.of(reqProd1.getId())));
        ContentDTO conditionalContent2 = adminClient.ownerContent().createContent(ownerKey, Content.random()
            .id(StringUtil.random(5, StringUtil.CHARSET_NUMERIC))
            .type("yum")
            .modifiedProductIds(Set.of(reqProd2.getId())));
        ContentDTO conditionalContent3 = adminClient.ownerContent().createContent(ownerKey, Content.random()
            .id(StringUtil.random(5, StringUtil.CHARSET_NUMERIC))
            .type("yum")
            .modifiedProductIds(Set.of(reqProd3.getId())));
        adminClient.ownerProducts()
            .addContent(ownerKey, dependentProvProd.getId(), conditionalContent1.getId(), true);
        adminClient.ownerProducts()
            .addContent(ownerKey, dependentProvProd.getId(), conditionalContent2.getId(), true);
        adminClient.ownerProducts()
            .addContent(ownerKey, dependentProvProd.getId(), conditionalContent3.getId(), true);

        ProductDTO dependentProd = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.random()
            .providedProducts(Set.of(dependentProvProd)));
        adminClient.owners().createPool(ownerKey, Pools.random(dependentProd));

        // Bind to the normal subscription first
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("system.certificate_version", "1.0")));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumerClient.consumers().bindProduct(consumer.getUuid(), bundledProd2.getId());

        // Bind to the dependent subscription which requires the product(s) provided by the previously
        // bound subscription:
        JsonNode ents = consumerClient.consumers().bindProduct(consumer.getUuid(), dependentProd.getId());
        String cert = getCertFromEnt(ents.get(0));
        assertThat(getContentRepoType(cert, conditionalContent1.getId()))
            .isEqualTo(conditionalContent1.getType());
        assertThat(getContentRepoType(cert, conditionalContent2.getId()))
            .isEqualTo(conditionalContent2.getType());
        assertNull(getContentRepoType(cert, conditionalContent3.getId()));
    }

    @Test
    public void shouldNotIncludeConditionalContentWithoutTheRequiredProducts() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();
        ProductDTO reqProd1 = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng());
        ProductDTO reqProd2 = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng());
        ProductDTO reqProd3 = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng());

        // Create our dependent provided product, which carries content sets -- each of which of which
        // requires one of the provided products above
        ProductDTO dependentProvProd = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.random());
        ContentDTO conditionalContent1 = adminClient.ownerContent().createContent(ownerKey, Content.random()
            .modifiedProductIds(Set.of(reqProd1.getId())));
        ContentDTO conditionalContent2 = adminClient.ownerContent().createContent(ownerKey, Content.random()
            .modifiedProductIds(Set.of(reqProd2.getId())));
        ContentDTO conditionalContent3 = adminClient.ownerContent().createContent(ownerKey, Content.random()
            .modifiedProductIds(Set.of(reqProd3.getId())));
        adminClient.ownerProducts()
            .addContent(ownerKey, dependentProvProd.getId(), conditionalContent1.getId(), true);
        adminClient.ownerProducts()
            .addContent(ownerKey, dependentProvProd.getId(), conditionalContent2.getId(), true);
        adminClient.ownerProducts()
            .addContent(ownerKey, dependentProvProd.getId(), conditionalContent3.getId(), true);

        ProductDTO dependentProd = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.random()
            .providedProducts(Set.of(dependentProvProd)));
        adminClient.owners().createPool(ownerKey, Pools.random(dependentProd));

        // Bind to the dependent subscription without being entitled to any of the required products
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("system.certificate_version", "1.0")));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        JsonNode ents = consumerClient.consumers().bindProduct(consumer.getUuid(), dependentProd.getId());
        String cert = getCertFromEnt(ents.get(0));
        assertNull(getContentRepoType(cert, conditionalContent1.getId()));
        assertNull(getContentRepoType(cert, conditionalContent2.getId()));
        assertNull(getContentRepoType(cert, conditionalContent3.getId()));
    }

    @Test
    public void shouldRegenerateCertificateWhenConsumerReceivesAccessToARequiredProduct() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();
        ProductDTO reqProd1 = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng());
        ProductDTO reqProd2 = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng());
        ProductDTO reqProd3 = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng());
        ProductDTO bundledProd = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng()
            .providedProducts(Set.of(reqProd1, reqProd2, reqProd3)));
        adminClient.owners().createPool(ownerKey, Pools.random(bundledProd));

        ProductDTO dependentProvProd = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng());
        ContentDTO conditionalContent1 = adminClient.ownerContent().createContent(ownerKey, Content.random()
            .id(StringUtil.random(5, StringUtil.CHARSET_NUMERIC))
            .type("yum")
            .modifiedProductIds(Set.of(reqProd1.getId())));
        ContentDTO conditionalContent2 = adminClient.ownerContent().createContent(ownerKey, Content.random()
            .id(StringUtil.random(5, StringUtil.CHARSET_NUMERIC))
            .type("yum")
            .modifiedProductIds(Set.of(reqProd2.getId())));
        ContentDTO conditionalContent3 = adminClient.ownerContent().createContent(ownerKey, Content.random()
            .id(StringUtil.random(5, StringUtil.CHARSET_NUMERIC))
            .type("yum")
            .modifiedProductIds(Set.of(reqProd3.getId())));
        adminClient.ownerProducts()
            .addContent(ownerKey, dependentProvProd.getId(), conditionalContent1.getId(), true);
        adminClient.ownerProducts()
            .addContent(ownerKey, dependentProvProd.getId(), conditionalContent2.getId(), true);
        adminClient.ownerProducts()
            .addContent(ownerKey, dependentProvProd.getId(), conditionalContent3.getId(), true);

        ProductDTO dependentProd = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng()
            .providedProducts(Set.of(dependentProvProd)));
        adminClient.owners().createPool(ownerKey, Pools.random(dependentProd));

        // Bind to the dependent subscription without being entitled to any of the required products
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("system.certificate_version", "1.0")));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        JsonNode ents = consumerClient.consumers().bindProduct(consumer.getUuid(), dependentProd.getId());

        // Resulting dependent cert should not contain any of the conditional content sets
        String dependentProdCert = getCertFromEnt(ents.get(0));
        assertNull(getContentRepoType(dependentProdCert, conditionalContent1.getId()));
        assertNull(getContentRepoType(dependentProdCert, conditionalContent2.getId()));
        assertNull(getContentRepoType(dependentProdCert, conditionalContent3.getId()));
        JsonNode dependentProdCerts = ents.get(0).get("certificates");
        Long dependentProdCertSerial = dependentProdCerts.get(0).get("serial").get("serial").asLong();

        // Bind to the required product...
        ents = consumerClient.consumers().bindProduct(consumer.getUuid(), bundledProd.getId());
        assertThat(ents)
            .isNotNull()
            .singleElement();
        JsonNode bundledProdCerts = ents.get(0).get("certificates");
        Long bundledProdSerial = bundledProdCerts.get(0).get("serial").get("serial").asLong();

        // Impl note: The use of the Request/Reply objects are to avoid a Gson de-serialization
        // issue that occurs in the generated org.candlepin.invoker.client.JSON class. Gson de-serializes
        // the cert serial to a double by default and changes the serial value to an unexpected value.
        Response response = Request.from(consumerClient)
            .setPath("/consumers/{consumer_uuid}/certificates")
            .setPathParam("consumer_uuid", consumer.getUuid())
            .addHeader("accept", "application/json")
            .execute();

        // Old certificate should be gone
        Map<Long, String> serialToCert = getSerialToCert(response.getBodyAsString());
        assertThat(serialToCert.keySet())
            .hasSize(2)
            .doesNotContain(dependentProdCertSerial)
            .contains(bundledProdSerial);

        // Remove the pre-existing serial and cert leaving the newly generated serial and cert
        serialToCert.remove(bundledProdSerial);
        String newCertValue = serialToCert.entrySet().iterator().next().getValue();
        // And it should have the conditional content set
        assertThat(getContentRepoType(newCertValue, conditionalContent1.getId()))
            .isEqualTo(conditionalContent1.getType());
        assertThat(getContentRepoType(newCertValue, conditionalContent2.getId()))
            .isEqualTo(conditionalContent2.getType());
        assertThat(getContentRepoType(newCertValue, conditionalContent3.getId()))
            .isEqualTo(conditionalContent3.getType());
    }

    @Test
    public void shouldRegenerateWhenTheConsumerLosesAccessToRequiredProducts() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();
        ProductDTO reqProd1 = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng());
        ProductDTO reqProd2 = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng());
        ProductDTO reqProd3 = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng());
        ProductDTO bundledProd1 = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng()
            .providedProducts(Set.of(reqProd1, reqProd2, reqProd3)));
        adminClient.owners().createPool(ownerKey, Pools.random(bundledProd1));
        ProductDTO bundledProd2 = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng()
            .providedProducts(Set.of(reqProd1, reqProd2)));
        adminClient.owners().createPool(ownerKey, Pools.random(bundledProd2));

        // Create our dependent provided product, which carries content sets -- each of which of which
        // requires one of the provided products above
        ProductDTO dependentProvProd = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng());
        ContentDTO conditionalContent1 = adminClient.ownerContent().createContent(ownerKey, Content.random()
            .id(StringUtil.random(5, StringUtil.CHARSET_NUMERIC))
            .type("yum")
            .modifiedProductIds(Set.of(reqProd1.getId())));
        ContentDTO conditionalContent2 = adminClient.ownerContent().createContent(ownerKey, Content.random()
            .id(StringUtil.random(5, StringUtil.CHARSET_NUMERIC))
            .type("yum")
            .modifiedProductIds(Set.of(reqProd2.getId())));
        ContentDTO conditionalContent3 = adminClient.ownerContent().createContent(ownerKey, Content.random()
            .id(StringUtil.random(5, StringUtil.CHARSET_NUMERIC))
            .type("yum")
            .modifiedProductIds(Set.of(reqProd3.getId())));
        adminClient.ownerProducts()
            .addContent(ownerKey, dependentProvProd.getId(), conditionalContent1.getId(), true);
        adminClient.ownerProducts()
            .addContent(ownerKey, dependentProvProd.getId(), conditionalContent2.getId(), true);
        adminClient.ownerProducts()
            .addContent(ownerKey, dependentProvProd.getId(), conditionalContent3.getId(), true);

        ProductDTO dependentProd = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng()
            .providedProducts(Set.of(dependentProvProd)));
        adminClient.owners().createPool(ownerKey, Pools.random(dependentProd));

        // Bind to the dependent subscription without being entitled to any of the required products
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("system.certificate_version", "1.0")));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        JsonNode ents = consumerClient.consumers().bindProduct(consumer.getUuid(), dependentProd.getId());
        assertThat(ents).singleElement();
        String dependentProdEntId = ents.get(0).get("id").asText();

        // Verify that we don't have any content repos yet...
        String cert = getCertFromEnt(ents.get(0));
        assertNull(getContentRepoType(cert, conditionalContent1.getId()));
        assertNull(getContentRepoType(cert, conditionalContent2.getId()));
        assertNull(getContentRepoType(cert, conditionalContent3.getId()));

        // Bind to a normal subscription...
        ents = consumerClient.consumers().bindProduct(consumer.getUuid(), bundledProd2.getId());
        assertThat(ents).isNotNull().singleElement();
        String entId = ents.get(0).get("id").asText();
        List<String> entsToRevoke = new ArrayList<>();
        entsToRevoke.add(entId);

        // Re-fetch the modifier entitlement...
        EntitlementDTO entitlement = consumerClient.entitlements().getEntitlement(dependentProdEntId);
        assertThat(entitlement)
            .isNotNull()
            .extracting(EntitlementDTO::getCertificates, as(collection(CertificateDTO.class)))
            .singleElement();

        // Modifier certificate should now contain some conditional content...
        String entCert = entitlement.getCertificates().iterator().next().getCert();
        assertThat(getContentRepoType(entCert, conditionalContent1.getId()))
            .isEqualTo(conditionalContent1.getType());
        assertThat(getContentRepoType(entCert, conditionalContent2.getId()))
            .isEqualTo(conditionalContent2.getType());
        assertNull(getContentRepoType(entCert, conditionalContent3.getId()));

        // Bind to another normal subscription...
        ents = consumerClient.consumers().bindProduct(consumer.getUuid(), bundledProd1.getId());
        assertThat(ents).isNotNull().singleElement();
        entId = ents.get(0).get("id").asText();
        entsToRevoke.add(entId);

        // Re-fetch the modifier entitlement...
        entitlement = consumerClient.entitlements().getEntitlement(dependentProdEntId);
        assertThat(entitlement)
            .isNotNull()
            .extracting(EntitlementDTO::getCertificates, as(collection(CertificateDTO.class)))
            .singleElement();

        // Modifier certificate should now contain all conditional content...
        entCert = entitlement.getCertificates().iterator().next().getCert();
        assertThat(getContentRepoType(entCert, conditionalContent1.getId()))
            .isEqualTo(conditionalContent1.getType());
        assertThat(getContentRepoType(entCert, conditionalContent2.getId()))
            .isEqualTo(conditionalContent2.getType());
        assertThat(getContentRepoType(entCert, conditionalContent3.getId()))
            .isEqualTo(conditionalContent3.getType());

        // Unbind the pools to revoke our entitlements...
        revokeEnts(consumerClient, consumer.getUuid(), entsToRevoke);

        // Re-fetch the modifier entitlement...
        entitlement = consumerClient.entitlements().getEntitlement(dependentProdEntId);
        assertThat(entitlement)
            .isNotNull()
            .extracting(EntitlementDTO::getCertificates, as(collection(CertificateDTO.class)))
            .singleElement();

        // Verify that we don't have any content repos anymore
        entCert = entitlement.getCertificates().iterator().next().getCert();
        assertNull(getContentRepoType(entCert, conditionalContent1.getId()));
        assertNull(getContentRepoType(entCert, conditionalContent2.getId()));
        assertNull(getContentRepoType(entCert, conditionalContent3.getId()));
    }

    @Test
    public void shouldRegenerateWhenTheRequiredProductSubscriptionDisappears() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();
        ProductDTO reqProd1 = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng());
        ProductDTO reqProd2 = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng());
        ProductDTO reqProd3 = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng());
        ProductDTO bundledProd1 = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng()
            .providedProducts(Set.of(reqProd1, reqProd2, reqProd3)));
        PoolDTO bundledPool1 = adminClient.owners().createPool(ownerKey, Pools.random(bundledProd1));
        ProductDTO bundledProd2 = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng()
            .providedProducts(Set.of(reqProd1, reqProd2)));
        PoolDTO bundledPool2 = adminClient.owners().createPool(ownerKey, Pools.random(bundledProd2));

        // Create our dependent provided product, which carries content sets -- each of which of which
        // requires one of the provided products above
        ProductDTO dependentProvProd = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng());
        ContentDTO conditionalContent1 = adminClient.ownerContent().createContent(ownerKey, Content.random()
            .id(StringUtil.random(5, StringUtil.CHARSET_NUMERIC))
            .type("yum")
            .modifiedProductIds(Set.of(reqProd1.getId())));
        ContentDTO conditionalContent2 = adminClient.ownerContent().createContent(ownerKey, Content.random()
            .id(StringUtil.random(5, StringUtil.CHARSET_NUMERIC))
            .type("yum")
            .modifiedProductIds(Set.of(reqProd2.getId())));
        ContentDTO conditionalContent3 = adminClient.ownerContent().createContent(ownerKey, Content.random()
            .id(StringUtil.random(5, StringUtil.CHARSET_NUMERIC))
            .type("yum")
            .modifiedProductIds(Set.of(reqProd3.getId())));
        adminClient.ownerProducts()
            .addContent(ownerKey, dependentProvProd.getId(), conditionalContent1.getId(), true);
        adminClient.ownerProducts()
            .addContent(ownerKey, dependentProvProd.getId(), conditionalContent2.getId(), true);
        adminClient.ownerProducts()
            .addContent(ownerKey, dependentProvProd.getId(), conditionalContent3.getId(), true);

        ProductDTO dependentProd = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng()
            .providedProducts(Set.of(dependentProvProd)));
        adminClient.owners().createPool(ownerKey, Pools.random(dependentProd));

        // Bind to the dependent subscription without being entitled to any of the required products
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("system.certificate_version", "1.0")));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        JsonNode ents = consumerClient.consumers().bindProduct(consumer.getUuid(), dependentProd.getId());
        assertThat(ents).singleElement();
        String dependentProdEntId = ents.get(0).get("id").asText();

        // Verify that we don't have any content repos yet...
        String cert = getCertFromEnt(ents.get(0));
        assertNull(getContentRepoType(cert, conditionalContent1.getId()));
        assertNull(getContentRepoType(cert, conditionalContent2.getId()));
        assertNull(getContentRepoType(cert, conditionalContent3.getId()));

        // Bind to a normal subscription...
        ents = consumerClient.consumers().bindProduct(consumer.getUuid(), bundledProd2.getId());
        assertThat(ents).isNotNull().singleElement();
        String entId = ents.get(0).get("id").asText();
        List<String> entsToRevoke = new ArrayList<>();
        entsToRevoke.add(entId);

        // Re-fetch the modifier entitlement...
        EntitlementDTO entitlement = consumerClient.entitlements().getEntitlement(dependentProdEntId);
        assertThat(entitlement)
            .isNotNull()
            .extracting(EntitlementDTO::getCertificates, as(collection(CertificateDTO.class)))
            .singleElement();

        // Modifier certificate should now contain some conditional content...
        String entCert = entitlement.getCertificates().iterator().next().getCert();
        assertThat(getContentRepoType(entCert, conditionalContent1.getId()))
            .isEqualTo(conditionalContent1.getType());
        assertThat(getContentRepoType(entCert, conditionalContent2.getId()))
            .isEqualTo(conditionalContent2.getType());
        assertNull(getContentRepoType(entCert, conditionalContent3.getId()));

        // Bind to another normal subscription...
        ents = consumerClient.consumers().bindProduct(consumer.getUuid(), bundledProd1.getId());
        assertThat(ents).isNotNull().singleElement();
        entId = ents.get(0).get("id").asText();
        entsToRevoke.add(entId);

        // Re-fetch the modifier entitlement...
        entitlement = consumerClient.entitlements().getEntitlement(dependentProdEntId);
        assertThat(entitlement)
            .isNotNull()
            .extracting(EntitlementDTO::getCertificates, as(collection(CertificateDTO.class)))
            .singleElement();

        // Modifier certificate should now contain all conditional content...
        entCert = entitlement.getCertificates().iterator().next().getCert();
        assertThat(getContentRepoType(entCert, conditionalContent1.getId()))
            .isEqualTo(conditionalContent1.getType());
        assertThat(getContentRepoType(entCert, conditionalContent2.getId()))
            .isEqualTo(conditionalContent2.getType());
        assertThat(getContentRepoType(entCert, conditionalContent3.getId()))
            .isEqualTo(conditionalContent3.getType());

        // Unbind the pools to revoke our entitlements...
        adminClient.pools().deletePool(bundledPool1.getId());
        adminClient.pools().deletePool(bundledPool2.getId());

        // Re-fetch the modifier entitlement...
        entitlement = consumerClient.entitlements().getEntitlement(dependentProdEntId);
        assertThat(entitlement)
            .isNotNull()
            .extracting(EntitlementDTO::getCertificates, as(collection(CertificateDTO.class)))
            .singleElement();

        // Verify that we don't have any content repos anymore
        entCert = entitlement.getCertificates().iterator().next().getCert();
        assertNull(getContentRepoType(entCert, conditionalContent1.getId()));
        assertNull(getContentRepoType(entCert, conditionalContent2.getId()));
        assertNull(getContentRepoType(entCert, conditionalContent3.getId()));
    }

    @Test
    public void shouldIncludeConditionalContentInV3CertAfterAutoAttachThatEntitlesTheRequiredProduct()
        throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();

        ProductDTO engProd1 = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng());
        String prod1Id = StringUtil.random("id-");
        ProductDTO prod1 = adminClient.ownerProducts().createProductByOwner(ownerKey, Products.random()
            .id(prod1Id)
            .providedProducts(Set.of(engProd1))
            .multiplier(1L)
            .attributes(List.of(ProductAttributes.StackingId.withValue(prod1Id))));

        ProductDTO engProd2 = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng());
        String prod2Id = StringUtil.random("id-");
        ProductDTO prod2 = adminClient.ownerProducts().createProductByOwner(ownerKey, Products.random()
            .id(prod2Id)
            .providedProducts(Set.of(engProd2))
            .multiplier(1L)
            .attributes(List.of(ProductAttributes.StackingId.withValue(prod2Id))));

        ContentDTO engProd2Content = adminClient.ownerContent().createContent(ownerKey, Content.random());

        // Content that has a required/modified product 'engProd2' (this eng product needs to be entitled
        // to the consumer already, or otherwise this content will get filtered out during entitlement
        // cert generation)
        ContentDTO engProd1Content = adminClient.ownerContent().createContent(ownerKey, Content.random()
            .modifiedProductIds(Set.of(engProd2.getId())));

        adminClient.ownerProducts().addContent(ownerKey, engProd2.getId(), engProd2Content.getId(), true);
        adminClient.ownerProducts().addContent(ownerKey, engProd1.getId(), engProd1Content.getId(), true);

        // Creating master pool for prod2
        PoolDTO prod2Pool = adminClient.owners().createPool(ownerKey, Pools.random(prod2)
            .providedProducts(Set.of(Products.toProvidedProduct(engProd2)))
            .locked(true));

        // Create master pool for prod1
        PoolDTO prod1Pool = adminClient.owners().createPool(ownerKey, Pools.random(prod1)
            .providedProducts(Set.of(Products.toProvidedProduct(engProd1)))
            .locked(true));

        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner)
            .installedProducts(Set.of(Products.toInstalled(engProd1), Products.toInstalled(engProd2))));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        // Auto-attach the system
        JsonNode ents = consumerClient.consumers().autoBind(consumer.getUuid());
        assertThat(ents).isNotNull().hasSize(2);

        // Verify each entitlement cert contains the appropriate content set
        String firstEntPoolId = ents.get(0).get("pool").get("id").asText();
        String prod1Cert = firstEntPoolId.equals(prod1Pool.getId()) ? getCertFromEnt(ents.get(0)) :
            getCertFromEnt(ents.get(1));
        String prod2Cert = firstEntPoolId.equals(prod2Pool.getId()) ? getCertFromEnt(ents.get(0)) :
            getCertFromEnt(ents.get(1));
        assertThat(prod1Cert).isNotNull();
        assertThat(prod2Cert).isNotNull();

        JsonNode prod1EntCert = CertificateUtil.decodeAndUncompressCertificate(prod1Cert, ApiClient.MAPPER);
        JsonNode prod2EntCert = CertificateUtil.decodeAndUncompressCertificate(prod2Cert, ApiClient.MAPPER);

        JsonNode actualProd2EntCertContent = prod2EntCert.get("products").get(0).get("content");
        assertThat(actualProd2EntCertContent).hasSize(1);
        assertThat(actualProd2EntCertContent.get(0).get("id").asText())
            .isEqualTo(engProd2Content.getId());

        JsonNode actualProd1EntCertContent = prod1EntCert.get("products").get(0).get("content");
        assertThat(actualProd1EntCertContent).hasSize(1);
        assertThat(actualProd1EntCertContent.get(0).get("id").asText())
            .isEqualTo(engProd1Content.getId());
    }

    @Test
    public void shouldIncludeConditionalContentInV1CertAfterAutoAttachThatEntitlesTheRequiredProduct()
        throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();

        ProductDTO engProd1 = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng());
        String prod1Id = StringUtil.random("id-");
        ProductDTO prod1 = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.random()
            .id(prod1Id)
            .providedProducts(Set.of(engProd1))
            .multiplier(1L)
            .attributes(List.of(ProductAttributes.StackingId.withValue(prod1Id))));

        ProductDTO engProd2 = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng());
        String prod2Id = StringUtil.random("id-");
        ProductDTO prod2 = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.random()
            .id(prod2Id)
            .providedProducts(Set.of(engProd2))
            .multiplier(1L)
            .attributes(List.of(ProductAttributes.StackingId.withValue(prod2Id))));

        // Note: for v1 certificates, we only support certain types of content type, like 'yum', so we
        // must set the type to yum here, and also only numeric ids
        ContentDTO engProd2Content = adminClient.ownerContent().createContent(ownerKey, Content.random()
            .id(StringUtil.random(6, StringUtil.CHARSET_NUMERIC))
            .type("yum"));

        // Content that has a required/modified product 'engProd2' (this eng product needs to be entitled
        // to the consumer already, or otherwise this content will get filtered out during entitlement
        // cert generation)
        ContentDTO engProd1Content = adminClient.ownerContent().createContent(ownerKey, Content.random()
            .id(StringUtil.random(6, StringUtil.CHARSET_NUMERIC))
            .type("yum")
            .modifiedProductIds(Set.of(engProd2.getId())));

        adminClient.ownerProducts().addContent(ownerKey, engProd2.getId(), engProd2Content.getId(), true);
        adminClient.ownerProducts().addContent(ownerKey, engProd1.getId(), engProd1Content.getId(), true);

        // Creating master pool for prod2
        PoolDTO prod2Pool = adminClient.owners().createPool(ownerKey, Pools.random(prod2)
            .providedProducts(Set.of(Products.toProvidedProduct(engProd2)))
            .locked(true));

        // Create master pool for prod1
        PoolDTO prod1Pool = adminClient.owners().createPool(ownerKey, Pools.random(prod1)
            .providedProducts(Set.of(Products.toProvidedProduct(engProd1)))
            .locked(true));

        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("system.certificate_version", "1.0"))
            .installedProducts(Set.of(Products.toInstalled(engProd1), Products.toInstalled(engProd2))));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        // Auto-attach the system
        JsonNode ents = consumerClient.consumers().autoBind(consumer.getUuid());
        assertThat(ents).isNotNull().hasSize(2);

        // Verify each entitlement cert contains the appropriate content set
        String firstPoolId = ents.get(0).get("pool").get("id").asText();
        String prod1Cert = firstPoolId.equals(prod1Pool.getId()) ? getCertFromEnt(ents.get(0)) :
            getCertFromEnt(ents.get(1));
        String prod2Cert = firstPoolId.equals(prod2Pool.getId()) ? getCertFromEnt(ents.get(0)) :
            getCertFromEnt(ents.get(1));
        assertThat(prod1Cert).isNotNull();
        assertThat(prod2Cert).isNotNull();
        assertThat(getContentRepoType(prod1Cert, engProd1Content.getId()))
            .isEqualTo(engProd1Content.getType());
        assertThat(getContentName(prod1Cert, engProd1Content.getId()))
            .isEqualTo(engProd1Content.getName());
        assertThat(getContentRepoType(prod2Cert, engProd2Content.getId()))
            .isEqualTo(engProd2Content.getType());
        assertThat(getContentName(prod2Cert, engProd2Content.getId()))
            .isEqualTo(engProd2Content.getName());
    }

    private Map<Long, String> getSerialToCert(String export) throws JsonProcessingException {
        Map<Long, String> serialToCert = new HashMap<>();
        JsonNode root = ApiClient.MAPPER.readTree(export);
        root.forEach(cert -> {
            long serial = cert.get("serial").get("serial").asLong();
            String certValue = cert.get("cert").asText();

            serialToCert.put(serial, certValue);
        });

        return serialToCert;
    }

    private void revokeEnts(ApiClient client, String consumerId, Collection<String> entIds) {
        for (String entId : entIds) {
            client.consumers().unbindByEntitlementId(consumerId, entId);
        }
    }

    private String getCertFromEnt(JsonNode ent) {
        JsonNode certs = ent.get("certificates");
        assertThat(certs).isNotNull().isNotEmpty();

        return certs.get(0).get("cert").asText();
    }

    private String getContentRepoType(String cert, String contentId) {
        return CertificateUtil
            .standardExtensionValueFromCert(cert, CONTENT_OID + contentId + ".1");
    }

    private String getContentName(String cert, String contentId) {
        return CertificateUtil
            .standardExtensionValueFromCert(cert, CONTENT_OID + contentId + ".1.1");
    }
}