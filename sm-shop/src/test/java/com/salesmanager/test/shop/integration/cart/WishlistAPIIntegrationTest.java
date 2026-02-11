package com.salesmanager.test.shop.integration.cart;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;

import java.util.Optional;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * Characterization tests for the Wishlist (Save for Later) feature.
 *
 * These tests exercise the full lifecycle:
 *   1. Setup: register customer, create products, build a cart
 *   2. Save for later: move item from cart to wishlist
 *   3. Get wishlist: retrieve customer's wishlist
 *   4. Move to cart: move item from wishlist back to cart
 *   5. Remove from wishlist: delete item from wishlist
 *   6. Edge cases: empty wishlist, non-existent SKU, unauthenticated access
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class WishlistAPIIntegrationTest extends ServicesTestSupport {

	@Autowired
	private TestRestTemplate testRestTemplate;

	private static String customerToken;
	private static String cartCode;
	private static ReadableProduct product1;
	private static ReadableProduct product2;

	// ──────────────────────────────────────────────
	// Setup: Register customer and build a cart
	// ──────────────────────────────────────────────

	@Test
	@Order(1)
	public void registerCustomerAndGetToken() {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress("wishlist-test@test.com");
		customer.setPassword("Wishlist@123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");

		Address billing = new Address();
		billing.setFirstName("Wish");
		billing.setLastName("Tester");
		billing.setCountry("US");
		customer.setBilling(billing);
		customer.setStoreCode(Constants.DEFAULT_STORE);

		HttpEntity<PersistableCustomer> entity = new HttpEntity<>(customer, getHeader());
		ResponseEntity<PersistableCustomer> response = testRestTemplate.postForEntity(
				"/api/v1/customer/register", entity, PersistableCustomer.class);
		assertThat(response.getStatusCode(), is(OK));

		// Login as customer to get a customer-scoped JWT
		ResponseEntity<AuthenticationResponse> loginResponse = testRestTemplate.postForEntity(
				"/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest("wishlist-test@test.com", "Wishlist@123")),
				AuthenticationResponse.class);
		assertThat(loginResponse.getStatusCode(), is(OK));
		assertNotNull(loginResponse.getBody().getToken());

		customerToken = loginResponse.getBody().getToken();
	}

	@Test
	@Order(2)
	public void createProductsAndCart() throws Exception {
		// Create two sample products via the admin API helpers
		product1 = sampleProduct("wishProd1");
		assertNotNull(product1);

		product2 = sampleProduct("wishProd2");
		assertNotNull(product2);

		// Create an anonymous cart with product1
		PersistableShoppingCartItem cartItem1 = new PersistableShoppingCartItem();
		cartItem1.setProduct(product1.getSku());
		cartItem1.setQuantity(1);

		HttpEntity<PersistableShoppingCartItem> cartEntity = new HttpEntity<>(cartItem1, getHeader());
		ResponseEntity<ReadableShoppingCart> cartResponse = testRestTemplate.postForEntity(
				"/api/v1/cart/", cartEntity, ReadableShoppingCart.class);
		assertNotNull(cartResponse.getBody());
		cartCode = cartResponse.getBody().getCode();

		// Add product2 to the same cart
		PersistableShoppingCartItem cartItem2 = new PersistableShoppingCartItem();
		cartItem2.setProduct(product2.getSku());
		cartItem2.setQuantity(1);

		HttpEntity<PersistableShoppingCartItem> cartEntity2 = new HttpEntity<>(cartItem2, getHeader());
		ResponseEntity<ReadableShoppingCart> cartResponse2 = testRestTemplate.exchange(
				"/api/v1/cart/" + cartCode, HttpMethod.PUT, cartEntity2, ReadableShoppingCart.class);
		assertNotNull(cartResponse2.getBody());
		assertEquals(2, cartResponse2.getBody().getQuantity());

		// Merge anonymous cart with the authenticated customer so it's findable by customer ID
		HttpEntity<String> mergeEntity = new HttpEntity<>(customerHeaders());
		ResponseEntity<ReadableShoppingCart> mergedCart = testRestTemplate.exchange(
				"/api/v1/auth/customer/cart?cart=" + cartCode,
				HttpMethod.GET, mergeEntity, ReadableShoppingCart.class);
		assertThat(mergedCart.getStatusCode(), is(OK));
		assertNotNull(mergedCart.getBody().getCustomer());
		assertEquals(2, mergedCart.getBody().getQuantity());
	}

	// ──────────────────────────────────────────────
	// Happy path: Save for later → Get → Move back → Delete
	// ──────────────────────────────────────────────

	@Test
	@Order(3)
	public void saveForLater_movesItemFromCartToWishlist() {
		HttpEntity<String> entity = new HttpEntity<>(customerHeaders());
		ResponseEntity<ReadableShoppingCart> response = testRestTemplate.exchange(
				"/api/v1/auth/customer/wishlist/product/" + product1.getSku(),
				HttpMethod.POST, entity, ReadableShoppingCart.class);

		assertThat(response.getStatusCode(), is(OK));

		ReadableShoppingCart updatedCart = response.getBody();
		assertNotNull(updatedCart);
		// Cart should now only have product2
		assertEquals(1, updatedCart.getQuantity());
		assertEquals("cart", updatedCart.getType());
		assertTrue("Cart should only contain product2",
				updatedCart.getProducts().stream()
						.anyMatch(p -> p.getSku().equals(product2.getSku())));
		assertTrue("Cart should NOT contain product1",
				updatedCart.getProducts().stream()
						.noneMatch(p -> p.getSku().equals(product1.getSku())));
	}

	@Test
	@Order(4)
	public void getWishlist_returnsWishlistWithSavedItem() {
		HttpEntity<String> entity = new HttpEntity<>(customerHeaders());
		ResponseEntity<ReadableShoppingCart> response = testRestTemplate.exchange(
				"/api/v1/auth/customer/wishlist",
				HttpMethod.GET, entity, ReadableShoppingCart.class);

		assertThat(response.getStatusCode(), is(OK));

		ReadableShoppingCart wishlist = response.getBody();
		assertNotNull(wishlist);
		assertEquals("wishlist", wishlist.getType());
		assertEquals(1, wishlist.getQuantity());
		assertTrue("Wishlist should contain product1",
				wishlist.getProducts().stream()
						.anyMatch(p -> p.getSku().equals(product1.getSku())));
	}

	@Test
	@Order(5)
	public void moveToCart_movesItemFromWishlistBackToCart() {
		HttpEntity<String> entity = new HttpEntity<>(customerHeaders());
		ResponseEntity<ReadableShoppingCart> response = testRestTemplate.exchange(
				"/api/v1/auth/customer/cart/wishlist/" + product1.getSku(),
				HttpMethod.POST, entity, ReadableShoppingCart.class);

		assertThat(response.getStatusCode(), is(OK));

		ReadableShoppingCart updatedCart = response.getBody();
		assertNotNull(updatedCart);
		// Cart should now have both products again
		assertEquals(2, updatedCart.getQuantity());
		assertEquals("cart", updatedCart.getType());
		assertTrue("Cart should contain product1",
				updatedCart.getProducts().stream()
						.anyMatch(p -> p.getSku().equals(product1.getSku())));
		assertTrue("Cart should contain product2",
				updatedCart.getProducts().stream()
						.anyMatch(p -> p.getSku().equals(product2.getSku())));
	}

	@Test
	@Order(6)
	public void getWishlist_returns404WhenEmpty() {
		// After moveToCart, wishlist should be empty/gone
		HttpEntity<String> entity = new HttpEntity<>(customerHeaders());
		ResponseEntity<ReadableShoppingCart> response = testRestTemplate.exchange(
				"/api/v1/auth/customer/wishlist",
				HttpMethod.GET, entity, ReadableShoppingCart.class);

		assertThat(response.getStatusCode(), is(NOT_FOUND));
	}

	// ──────────────────────────────────────────────
	// Delete from wishlist
	// ──────────────────────────────────────────────

	@Test
	@Order(7)
	public void saveForLaterAgain_forDeleteTest() {
		HttpEntity<String> entity = new HttpEntity<>(customerHeaders());
		ResponseEntity<ReadableShoppingCart> response = testRestTemplate.exchange(
				"/api/v1/auth/customer/wishlist/product/" + product2.getSku(),
				HttpMethod.POST, entity, ReadableShoppingCart.class);

		assertThat(response.getStatusCode(), is(OK));
		assertEquals(1, response.getBody().getQuantity());
	}

	@Test
	@Order(8)
	public void removeFromWishlist_returns204() {
		HttpEntity<String> entity = new HttpEntity<>(customerHeaders());
		ResponseEntity<Void> response = testRestTemplate.exchange(
				"/api/v1/auth/customer/wishlist/product/" + product2.getSku(),
				HttpMethod.DELETE, entity, Void.class);

		assertThat(response.getStatusCode(), is(NO_CONTENT));
		assertNull(response.getBody());
	}

	@Test
	@Order(9)
	public void getWishlist_returns404AfterRemove() {
		HttpEntity<String> entity = new HttpEntity<>(customerHeaders());
		ResponseEntity<ReadableShoppingCart> response = testRestTemplate.exchange(
				"/api/v1/auth/customer/wishlist",
				HttpMethod.GET, entity, ReadableShoppingCart.class);

		assertThat(response.getStatusCode(), is(NOT_FOUND));
	}

	// ──────────────────────────────────────────────
	// Edge cases
	// ──────────────────────────────────────────────

	@Test
	@Order(10)
	public void saveForLater_nonExistentSku_returns404() {
		HttpEntity<String> entity = new HttpEntity<>(customerHeaders());
		ResponseEntity<ReadableShoppingCart> response = testRestTemplate.exchange(
				"/api/v1/auth/customer/wishlist/product/NON-EXISTENT-SKU",
				HttpMethod.POST, entity, ReadableShoppingCart.class);

		assertThat(response.getStatusCode(), is(NOT_FOUND));
	}

	@Test
	@Order(11)
	public void moveToCart_nonExistentSku_returns404() {
		// First, save product1 for later so we have a wishlist
		HttpEntity<String> entity = new HttpEntity<>(customerHeaders());
		testRestTemplate.exchange(
				"/api/v1/auth/customer/wishlist/product/" + product1.getSku(),
				HttpMethod.POST, entity, ReadableShoppingCart.class);

		// Now try to move a non-existent SKU from wishlist to cart
		ResponseEntity<ReadableShoppingCart> response = testRestTemplate.exchange(
				"/api/v1/auth/customer/cart/wishlist/NON-EXISTENT-SKU",
				HttpMethod.POST, entity, ReadableShoppingCart.class);

		assertThat(response.getStatusCode(), is(NOT_FOUND));
	}

	@Test
	@Order(12)
	public void removeFromWishlist_nonExistentSku_returns404() {
		HttpEntity<String> entity = new HttpEntity<>(customerHeaders());
		ResponseEntity<Void> response = testRestTemplate.exchange(
				"/api/v1/auth/customer/wishlist/product/NON-EXISTENT-SKU",
				HttpMethod.DELETE, entity, Void.class);

		assertThat(response.getStatusCode(), is(NOT_FOUND));
	}

	@Test
	@Order(13)
	public void saveForLater_multipleTimes_wishlistAccumulatesItems() {
		HttpEntity<String> entity = new HttpEntity<>(customerHeaders());

		// State after test 12: Wishlist has [product1], Cart is empty/gone.
		// Move product1 back to cart (creates a new cart).
		ResponseEntity<ReadableShoppingCart> moveResp = testRestTemplate.exchange(
				"/api/v1/auth/customer/cart/wishlist/" + product1.getSku(),
				HttpMethod.POST, entity, ReadableShoppingCart.class);
		assertThat(moveResp.getStatusCode(), is(OK));
		String currentCartCode = moveResp.getBody().getCode();

		// Re-add product2 to the cart so we have two items to save
		PersistableShoppingCartItem cartItem2 = new PersistableShoppingCartItem();
		cartItem2.setProduct(product2.getSku());
		cartItem2.setQuantity(1);
		HttpEntity<PersistableShoppingCartItem> addEntity = new HttpEntity<>(cartItem2, customerHeaders());
		ResponseEntity<ReadableShoppingCart> addResp = testRestTemplate.exchange(
				"/api/v1/cart/" + currentCartCode,
				HttpMethod.PUT, addEntity, ReadableShoppingCart.class);
		assertNotNull(addResp.getBody());
		assertEquals(2, addResp.getBody().getQuantity());

		// Save product1 for later
		ResponseEntity<ReadableShoppingCart> resp1 = testRestTemplate.exchange(
				"/api/v1/auth/customer/wishlist/product/" + product1.getSku(),
				HttpMethod.POST, entity, ReadableShoppingCart.class);
		assertThat(resp1.getStatusCode(), is(OK));

		// Save product2 for later
		ResponseEntity<ReadableShoppingCart> resp2 = testRestTemplate.exchange(
				"/api/v1/auth/customer/wishlist/product/" + product2.getSku(),
				HttpMethod.POST, entity, ReadableShoppingCart.class);
		assertThat(resp2.getStatusCode(), is(OK));

		// Wishlist should now contain both items
		ResponseEntity<ReadableShoppingCart> wishlistResp = testRestTemplate.exchange(
				"/api/v1/auth/customer/wishlist",
				HttpMethod.GET, entity, ReadableShoppingCart.class);
		assertThat(wishlistResp.getStatusCode(), is(OK));

		ReadableShoppingCart wishlist = wishlistResp.getBody();
		assertNotNull(wishlist);
		assertEquals("wishlist", wishlist.getType());
		assertEquals(2, wishlist.getQuantity());
	}

	@Test
	@Order(14)
	public void unauthenticated_wishlistEndpoints_returns401or403() {
		// No auth header at all
		ResponseEntity<String> getResponse = testRestTemplate.exchange(
				"/api/v1/auth/customer/wishlist",
				HttpMethod.GET, null, String.class);

		// Spring Security should reject unauthenticated requests
		assertTrue("Unauthenticated GET wishlist should return 401 or 403",
				getResponse.getStatusCodeValue() == 401 || getResponse.getStatusCodeValue() == 403);

		ResponseEntity<String> postResponse = testRestTemplate.exchange(
				"/api/v1/auth/customer/wishlist/product/SOME-SKU",
				HttpMethod.POST, null, String.class);
		assertTrue("Unauthenticated POST save-for-later should return 401 or 403",
				postResponse.getStatusCodeValue() == 401 || postResponse.getStatusCodeValue() == 403);
	}

	@Test
	@Order(15)
	public void moveToCart_createsCartIfNoneExists_andTypeIsCart() {
		// State after test 13: Wishlist has [product1, product2], Cart is empty/gone.
		// Moving an item to cart when no cart exists should create a new one with type "cart".
		HttpEntity<String> entity = new HttpEntity<>(customerHeaders());

		ResponseEntity<ReadableShoppingCart> moveResp = testRestTemplate.exchange(
				"/api/v1/auth/customer/cart/wishlist/" + product1.getSku(),
				HttpMethod.POST, entity, ReadableShoppingCart.class);
		assertThat(moveResp.getStatusCode(), is(OK));
		assertNotNull(moveResp.getBody());
		assertEquals("cart", moveResp.getBody().getType());
		assertEquals(1, moveResp.getBody().getQuantity());

		// Move the second item too
		ResponseEntity<ReadableShoppingCart> moveResp2 = testRestTemplate.exchange(
				"/api/v1/auth/customer/cart/wishlist/" + product2.getSku(),
				HttpMethod.POST, entity, ReadableShoppingCart.class);
		assertThat(moveResp2.getStatusCode(), is(OK));
		assertEquals("cart", moveResp2.getBody().getType());
		assertEquals(2, moveResp2.getBody().getQuantity());

		// Verify via the customer cart endpoint that cart type is "cart"
		ResponseEntity<ReadableShoppingCart> cartResp = testRestTemplate.exchange(
				"/api/v1/auth/customer/cart",
				HttpMethod.GET, entity, ReadableShoppingCart.class);
		assertThat(cartResp.getStatusCode(), is(OK));
		assertEquals("cart", cartResp.getBody().getType());
	}

	// ──────────────────────────────────────────────
	// Helper: build customer auth headers
	// ──────────────────────────────────────────────

	private HttpHeaders customerHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.add("Authorization", "Bearer " + customerToken);
		return headers;
	}
}
