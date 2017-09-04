/*
 * Copyright (C) 2017 Oasis Feng. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.oasisfeng.condom;

import android.Manifest.permission;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.UserHandle;
import android.provider.Settings;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.test.InstrumentationRegistry;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.ParametersAreNonnullByDefault;

import static android.content.Intent.FLAG_RECEIVER_REGISTERED_ONLY;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.HONEYCOMB_MR1;
import static android.os.Build.VERSION_CODES.JELLY_BEAN_MR1;
import static android.os.Build.VERSION_CODES.N;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

@ParametersAreNonnullByDefault
public class CondomContextBlockingTest {

	@Test public void testSelfTargeted() {
		final TestContext context = new TestContext();
		final CondomContext condom = CondomContext.wrap(context, TAG), dry_condom = CondomContext.wrap(context, TAG, new CondomOptions().setDryRun(true));

		// Self-targeting test
		final String self_pkg = condom.getPackageName();
		final Intent[] self_targeted_intents = new Intent[] {
				intent().setPackage(self_pkg),
				intent().setComponent(new ComponentName(self_pkg, "X"))
		};
		with(self_targeted_intents, allBroadcastApis(condom), context.EXPECT_BASE_CALLED, context.expectFlags(0));
		with(self_targeted_intents, allServiceApis(condom), context.EXPECT_BASE_CALLED, context.expectFlags(0));
		with(self_targeted_intents, allBroadcastApis(dry_condom), context.EXPECT_BASE_CALLED, context.expectFlags(0));
		with(self_targeted_intents, allServiceApis(dry_condom), context.EXPECT_BASE_CALLED, context.expectFlags(0));
	}

	@Test public void testPreventNone() {
		final TestContext context = new TestContext();
		final CondomOptions options = new CondomOptions().preventServiceInBackgroundPackages(false).preventBroadcastToBackgroundPackages(false);
		final CondomContext condom = CondomContext.wrap(context, TAG, options), dry_condom = CondomContext.wrap(context, TAG, options.setDryRun(true));
		//noinspection deprecation, intentional test for deprecated method
		condom.preventWakingUpStoppedPackages(false);
		//noinspection deprecation
		dry_condom.preventWakingUpStoppedPackages(false);

		with(ALL_SORT_OF_INTENTS, allBroadcastApis(condom), context.EXPECT_BASE_CALLED, context.expectFlags(0));
		with(ALL_SORT_OF_INTENTS, allServiceApis(condom), context.EXPECT_BASE_CALLED, context.expectFlags(0));
		with(ALL_SORT_OF_INTENTS, allBroadcastApis(dry_condom), context.EXPECT_BASE_CALLED, context.expectFlags(0));
		with(ALL_SORT_OF_INTENTS, allServiceApis(dry_condom), context.EXPECT_BASE_CALLED, context.expectFlags(0));
	}

	@Test public void testPreventWakingUpStoppedPackages_IncludingDryRun() {
		final Intent[] intents_with_inc_stop = ALL_SORT_OF_INTENTS.clone();
		for (int i = 0; i < intents_with_inc_stop.length; i++)
			intents_with_inc_stop[i] = new Intent(intents_with_inc_stop[i]).addFlags(FLAG_INCLUDE_STOPPED_PACKAGES);
		final TestContext context = new TestContext();
		final CondomOptions options = new CondomOptions().preventBroadcastToBackgroundPackages(false).preventServiceInBackgroundPackages(false);
		final CondomContext condom = CondomContext.wrap(context, TAG, options), dry_condom = CondomContext.wrap(context, TAG, options.setDryRun(true));
		with(intents_with_inc_stop, allBroadcastApis(condom), context.EXPECT_BASE_CALLED, context.expectFlags(FLAG_EXCLUDE_STOPPED_PACKAGES));
		with(intents_with_inc_stop, allServiceApis(condom), context.EXPECT_BASE_CALLED, context.expectFlags(FLAG_EXCLUDE_STOPPED_PACKAGES));
		with(intents_with_inc_stop, allBroadcastApis(dry_condom), context.EXPECT_BASE_CALLED, context.expectFlags(FLAG_INCLUDE_STOPPED_PACKAGES));
		with(intents_with_inc_stop, allServiceApis(dry_condom), context.EXPECT_BASE_CALLED, context.expectFlags(FLAG_INCLUDE_STOPPED_PACKAGES));
	}

	@Test public void testPreventBroadcastToBackgroundPackages() {
		final TestContext context = new TestContext();
		final CondomOptions options = new CondomOptions().preventBroadcastToBackgroundPackages(true);
		final CondomContext condom = CondomContext.wrap(context, TAG, options), dry_condom = CondomContext.wrap(context, TAG, options.setDryRun(true));
		final int extra_flag = SDK_INT >= N ? CondomCore.FLAG_RECEIVER_EXCLUDE_BACKGROUND : FLAG_RECEIVER_REGISTERED_ONLY;
		with(ALL_SORT_OF_INTENTS, allBroadcastApis(condom), context.EXPECT_BASE_CALLED, context.expectFlags(FLAG_EXCLUDE_STOPPED_PACKAGES | extra_flag));
		with(ALL_SORT_OF_INTENTS, allBroadcastApis(dry_condom), context.EXPECT_BASE_CALLED, context.expectFlags(0));
	}

	@Test public void testPreventServiceInBackgroundPackages() {
		final TestContext context = new TestContext();
		context.mTestingBackgroundUid = true;
		final CondomOptions options = new CondomOptions().preventServiceInBackgroundPackages(true).preventBroadcastToBackgroundPackages(false);
		final CondomContext condom = CondomContext.wrap(context, TAG, options), dry_condom = CondomContext.wrap(context, TAG, options.setDryRun(true));
		assertEquals(3, condom.getPackageManager().queryIntentServices(intent(), 0).size());
		context.assertBaseCalled();
		assertEquals(4, dry_condom.getPackageManager().queryIntentServices(intent(), 0).size());
		context.assertBaseCalled();
		assertEquals("non.bg.service", condom.getPackageManager().resolveService(intent(), 0).serviceInfo.packageName);
		context.assertBaseCalled();
		assertEquals(7777777, dry_condom.getPackageManager().resolveService(intent(), 0).serviceInfo.applicationInfo.uid);
		context.assertBaseCalled();
	}

	@Test public void testContentProviderOutboundJudge() {
		final TestContext context = new TestContext();
		final CondomOptions options = new CondomOptions().setOutboundJudge(new OutboundJudge() { @Override public boolean shouldAllow(final OutboundType type, final @Nullable Intent intent, final String target_pkg) {
			final String settings_pkg = InstrumentationRegistry.getTargetContext().getPackageManager().resolveContentProvider(Settings.System.CONTENT_URI.getAuthority(), 0).packageName;
			return ! settings_pkg.equals(target_pkg);
		}});
		final CondomContext condom = CondomContext.wrap(context, TAG, options), dry_condom = CondomContext.wrap(context, TAG, options.setDryRun(true));

		assertNull(condom.getPackageManager().resolveContentProvider(Settings.AUTHORITY, 0));
		assertNotNull(dry_condom.getPackageManager().resolveContentProvider(Settings.AUTHORITY, 0));
		assertNull(condom.getContentResolver().acquireContentProviderClient(Settings.System.CONTENT_URI));
		assertNotNull(dry_condom.getContentResolver().acquireContentProviderClient(Settings.System.CONTENT_URI));
	}

	@Test public void testContentProvider() {
		final TestContext context = new TestContext();
		final CondomContext condom = CondomContext.wrap(context, TAG), dry_condom = CondomContext.wrap(context, TAG, new CondomOptions().setDryRun(true));

		// Regular provider access
		final String android_id = Settings.System.getString(context.getContentResolver(), Settings.System.ANDROID_ID);
		assertNotNull(android_id);
		final String condom_android_id = Settings.System.getString(condom.getContentResolver(), Settings.System.ANDROID_ID);
		assertEquals(android_id, condom_android_id);
		final String dry_android_id = Settings.System.getString(dry_condom.getContentResolver(), Settings.System.ANDROID_ID);
		assertEquals(android_id, dry_android_id);

		context.mTestingStoppedProvider = true;
		// Prevent stopped packages,
		assertNull(condom.getPackageManager().resolveContentProvider(TEST_AUTHORITY, 0));
		assertNotNull(dry_condom.getPackageManager().resolveContentProvider(TEST_AUTHORITY, 0));
		assertNull(condom.getContentResolver().acquireContentProviderClient(TEST_CONTENT_URI));
		assertNotNull(dry_condom.getContentResolver().acquireContentProviderClient(TEST_CONTENT_URI));

		// Providers in system package should not be blocked.
		assertNotNull(condom.getPackageManager().resolveContentProvider(Settings.AUTHORITY, 0));
		assertNotNull(dry_condom.getPackageManager().resolveContentProvider(Settings.AUTHORITY, 0));
		assertNotNull(condom.getContentResolver().acquireContentProviderClient(Settings.System.CONTENT_URI));
		assertNotNull(dry_condom.getContentResolver().acquireContentProviderClient(Settings.System.CONTENT_URI));
		context.mTestingStoppedProvider = false;
	}
	private static final String TEST_AUTHORITY = "com.oasisfeng.condom.test";
	private static final Uri TEST_CONTENT_URI = Uri.parse("content://" + TEST_AUTHORITY + "/");

	public static class TestProvider extends ContentProvider {
		@Override public boolean onCreate() { return true; }
		@Nullable @Override public Cursor query(@NonNull final Uri uri, @Nullable final String[] strings, @Nullable final String s, @Nullable final String[] strings1, @Nullable final String s1) { return null; }
		@Nullable @Override public String getType(@NonNull final Uri uri) { return null; }
		@Nullable @Override public Uri insert(@NonNull final Uri uri, @Nullable final ContentValues contentValues) { return null; }
		@Override public int delete(@NonNull final Uri uri, @Nullable final String s, @Nullable final String[] strings) { return 0; }
		@Override public int update(@NonNull final Uri uri, @Nullable final ContentValues contentValues, @Nullable final String s, @Nullable final String[] strings) { return 0; }
	}

	@Test public void testOutboundJudge() {
		final TestContext context = new TestContext();
		final CondomOptions options = new CondomOptions().setOutboundJudge(new OutboundJudge() {
			@Override public boolean shouldAllow(final OutboundType type, final @Nullable Intent intent, final String target_pkg) {
				mNumOutboundJudgeCalled.incrementAndGet();
				return ! DISALLOWED_PACKAGE.equals(target_pkg);
			}
		});
		final CondomContext condom = CondomContext.wrap(context, TAG, options), dry_condom = CondomContext.wrap(context, TAG, options.setDryRun(true));
		final PackageManager pm = condom.getPackageManager(), dry_pm = dry_condom.getPackageManager();

		final Runnable EXPECT_OUTBOUND_JUDGE_REFUSAL = new Runnable() { @Override public void run() {
			context.assertBaseNotCalled();
			assertOutboundJudgeCalled(1);
		}};
		final Runnable EXPECT_OUTBOUND_JUDGE_PASS = new Runnable() { @Override public void run() {
			context.assertBaseCalled();
			assertOutboundJudgeCalled(1);
		}};
		with(DISALLOWED_INTENTS, allBroadcastApis(condom), EXPECT_OUTBOUND_JUDGE_REFUSAL);
		with(ALLOWED_INTENTS, allBroadcastApis(condom), EXPECT_OUTBOUND_JUDGE_PASS);
		with(DISALLOWED_INTENTS, allBroadcastApis(dry_condom), EXPECT_OUTBOUND_JUDGE_PASS);
		with(ALLOWED_INTENTS, allBroadcastApis(dry_condom), EXPECT_OUTBOUND_JUDGE_PASS);

		assertNull(pm.resolveService(intent().setPackage(DISALLOWED_PACKAGE), 0));
		context.assertBaseNotCalled();
		assertOutboundJudgeCalled(1);
		assertNotNull(dry_pm.resolveService(intent().setPackage(DISALLOWED_PACKAGE), 0));
		context.assertBaseCalled();
		assertOutboundJudgeCalled(1);

		assertEquals(1, pm.queryIntentServices(intent(), 0).size());
		context.assertBaseCalled();
		assertOutboundJudgeCalled(2);
		assertEquals(2, dry_pm.queryIntentServices(intent(), 0).size());
		context.assertBaseCalled();
		assertOutboundJudgeCalled(2);

		assertEquals(1, pm.queryBroadcastReceivers(intent(), 0).size());
		context.assertBaseCalled();
		assertOutboundJudgeCalled(2);
		assertEquals(2, dry_pm.queryBroadcastReceivers(intent(), 0).size());
		context.assertBaseCalled();
		assertOutboundJudgeCalled(2);

		condom.sendBroadcast(intent());
		context.assertBaseCalled();
		assertOutboundJudgeCalled(0);
		dry_condom.sendBroadcast(intent());
		context.assertBaseCalled();
		assertOutboundJudgeCalled(0);
	}

	private static void with(final Intent[] intents, final Consumer<Intent>[] tests, final Runnable... expectations) {
		for (final Intent intent : intents)
			for (final Consumer<Intent> test : tests) {
				test.accept(intent);
				for (final Runnable expectation : expectations) expectation.run();
			}
	}

	private static Intent intent() { return new Intent("com.example.TEST").addFlags(INTENT_FLAGS); }

	private static final UserHandle USER = SDK_INT >= JELLY_BEAN_MR1 ? android.os.Process.myUserHandle() : null;
	private static final int INTENT_FLAGS = Intent.FLAG_DEBUG_LOG_RESOLUTION | Intent.FLAG_FROM_BACKGROUND;	// Just random flags to verify flags preservation.
	private static final ServiceConnection SERVICE_CONNECTION = new ServiceConnection() {
		@Override public void onServiceConnected(final ComponentName name, final IBinder service) {}
		@Override public void onServiceDisconnected(final ComponentName name) {}
	};
	private static final String DISALLOWED_PACKAGE = "a.b.c";
	private static final String ALLOWED_PACKAGE = "x.y.z";
	private static final ComponentName DISALLOWED_COMPONENT = new ComponentName(DISALLOWED_PACKAGE, "A");
	private static final ComponentName ALLOWED_COMPONENT = new ComponentName(ALLOWED_PACKAGE, "A");
	private static final int FLAG_EXCLUDE_STOPPED_PACKAGES = SDK_INT >= HONEYCOMB_MR1 ? Intent.FLAG_EXCLUDE_STOPPED_PACKAGES : 0;
	private static final int FLAG_INCLUDE_STOPPED_PACKAGES = SDK_INT >= HONEYCOMB_MR1 ? Intent.FLAG_INCLUDE_STOPPED_PACKAGES : 0;

	private static final Intent[] ALL_SORT_OF_INTENTS = new Intent[] {
			intent(),
			intent().setPackage(ALLOWED_PACKAGE),
			intent().setPackage(DISALLOWED_PACKAGE),
			intent().setComponent(ALLOWED_COMPONENT),
			intent().setComponent(DISALLOWED_COMPONENT),
	};

	private static final Intent[] ALLOWED_INTENTS = new Intent[] {
			intent().setPackage(ALLOWED_PACKAGE),
			intent().setComponent(ALLOWED_COMPONENT),
	};

	private static final Intent[] DISALLOWED_INTENTS = new Intent[] {
			intent().setPackage(DISALLOWED_PACKAGE),
			intent().setComponent(DISALLOWED_COMPONENT),
	};

	private static Consumer<Intent>[] allBroadcastApis(final CondomContext condom) {
		final List<Consumer<Intent>> tests = new ArrayList<>();
		tests.add(new Consumer<Intent>() { @Override public void accept(final Intent intent) { condom.sendBroadcast(intent); }});
		tests.add(new Consumer<Intent>() { @Override public void accept(final Intent intent) { condom.sendBroadcast(intent, permission.DUMP); }});
		tests.add(new Consumer<Intent>() { @Override public void accept(final Intent intent) { condom.sendOrderedBroadcast(intent, permission.DUMP); }});
		tests.add(new Consumer<Intent>() { @Override public void accept(final Intent intent) { condom.sendOrderedBroadcast(intent, permission.DUMP, null, null, 0, null, null); }});
		tests.add(new Consumer<Intent>() { @Override public void accept(final Intent intent) { condom.sendStickyBroadcast(intent); }});
		tests.add(new Consumer<Intent>() { @Override public void accept(final Intent intent) { condom.sendStickyOrderedBroadcast(intent, null, null, 0, null, null); }});
		if (SDK_INT >= JELLY_BEAN_MR1) {
			tests.add(new Consumer<Intent>() { @TargetApi(JELLY_BEAN_MR1) @Override public void accept(final Intent intent) { condom.sendBroadcastAsUser(intent, USER); }});
			tests.add(new Consumer<Intent>() { @TargetApi(JELLY_BEAN_MR1) @Override public void accept(final Intent intent) { condom.sendBroadcastAsUser(intent, USER, null); }});
			tests.add(new Consumer<Intent>() { @TargetApi(JELLY_BEAN_MR1) @Override public void accept(final Intent intent) { condom.sendStickyBroadcastAsUser(intent, USER); }});
			tests.add(new Consumer<Intent>() { @TargetApi(JELLY_BEAN_MR1) @Override public void accept(final Intent intent) { condom.sendOrderedBroadcastAsUser(intent, USER, null, null, null, 0, null, null); }});
			tests.add(new Consumer<Intent>() { @TargetApi(JELLY_BEAN_MR1) @Override public void accept(final Intent intent) { condom.sendStickyOrderedBroadcastAsUser(intent, USER,null, null, 0, null, null); }});
		}
		tests.add(new Consumer<Intent>() { @Override public void accept(final Intent intent) { condom.getPackageManager().queryBroadcastReceivers(intent, 0); }});

		//noinspection unchecked
		return tests.toArray(new Consumer[tests.size()]);
	}

	@SuppressWarnings("unchecked") private static Consumer<Intent>[] allServiceApis(final CondomContext condom) {
		return new Consumer[] {
				new Consumer<Intent>() { @Override public void accept(final Intent intent) {
					condom.startService(intent);
				}}, new Consumer<Intent>() { @Override public void accept(final Intent intent) {
					condom.bindService(intent, SERVICE_CONNECTION, 0);
				}}
		};
	}

	private void assertOutboundJudgeCalled(final int count) { assertEquals(count, mNumOutboundJudgeCalled.getAndSet(0)); }

	private final AtomicInteger mNumOutboundJudgeCalled = new AtomicInteger();
	private static final String TAG = "Test";


	private class TestContext extends ContextWrapper {

		@CallSuper void check(final Intent intent) {
			assertBaseNotCalled();
			mBaseCalled = true;
			mIntentFlags = intent.getFlags();
		}

		@Override public ComponentName startService(final Intent intent) { check(intent); return null; }
		@Override public boolean bindService(final Intent intent, final ServiceConnection c, final int f) { check(intent); return false; }
		@Override public void sendBroadcast(final Intent intent) { check(intent); }
		@Override public void sendBroadcast(final Intent intent, final String p) { check(intent); }
		@Override public void sendBroadcastAsUser(final Intent intent, final UserHandle user) { check(intent); }
		@Override public void sendBroadcastAsUser(final Intent intent, final UserHandle user, final String receiverPermission) { check(intent); }
		@Override public void sendStickyBroadcast(final Intent intent) { check(intent); }
		@Override public void sendStickyBroadcastAsUser(final Intent intent, final UserHandle u) { check(intent); }
		@Override public void sendOrderedBroadcast(final Intent intent, final String p) { check(intent); }
		@Override public void sendOrderedBroadcast(final Intent intent, final String p, final BroadcastReceiver r, final Handler s, final int c, final String d, final Bundle e) { check(intent); }
		@Override public void sendOrderedBroadcastAsUser(final Intent intent, final UserHandle u, final String p, final BroadcastReceiver r, final Handler s, final int c, final String d, final Bundle e) { check(intent); }
		@Override public void sendStickyOrderedBroadcast(final Intent intent, final BroadcastReceiver r, final Handler s, final int c, final String d, final Bundle e) { check(intent); }
		@Override public void sendStickyOrderedBroadcastAsUser(final Intent intent, final UserHandle u, final BroadcastReceiver r, final Handler s, final int c, final String d, final Bundle e) { check(intent); }

		@Override public PackageManager getPackageManager() {
			return new PackageManagerWrapper(InstrumentationRegistry.getTargetContext().getPackageManager()) {

				@Override public ResolveInfo resolveService(final Intent intent, final int flags) {
					check(intent);
					return buildResolveInfo(DISALLOWED_PACKAGE, true, 7777777);	// Must be consistent with the first entry from queryIntentServices().
				}

				@Override public List<ResolveInfo> queryIntentServices(final Intent intent, final int flags) {
					check(intent);
					final List<ResolveInfo> resolves = new ArrayList<>();
					if (mTestingBackgroundUid) {
						final ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
						final List<ActivityManager.RunningServiceInfo> services = am.getRunningServices(32);
						if (services != null) for (final ActivityManager.RunningServiceInfo service : services) {
							if (service.uid == android.os.Process.myUid()) continue;
							resolves.add(buildResolveInfo(DISALLOWED_PACKAGE, true, 7777777));	// Simulate a background UID.
							resolves.add(buildResolveInfo("non.bg.service", true, service.uid));
							break;
						}
					}
					resolves.add(buildResolveInfo(ALLOWED_PACKAGE, true, android.os.Process.myUid()));
					resolves.add(buildResolveInfo(DISALLOWED_PACKAGE, true, android.os.Process.myUid()));
					return resolves;
				}

				@Override public List<ResolveInfo> queryBroadcastReceivers(final Intent intent, final int flags) {
					check(intent);
					final List<ResolveInfo> resolves = new ArrayList<>();
					resolves.add(buildResolveInfo(ALLOWED_PACKAGE, false, android.os.Process.myUid()));
					resolves.add(buildResolveInfo(DISALLOWED_PACKAGE, false, android.os.Process.myUid()));
					return resolves;
				}

				@Override public ProviderInfo resolveContentProvider(final String name, final int flags) {
					final ProviderInfo info = super.resolveContentProvider(name, flags);
					if (info != null && mTestingStoppedProvider) {
						if (getPackageName().equals(info.packageName)) info.packageName += ".dummy";	// To simulate a package other than current one.
						info.applicationInfo.flags |= ApplicationInfo.FLAG_STOPPED;
					}
					return info;
				}

				private ResolveInfo buildResolveInfo(final String pkg, final boolean service_or_receiver, final int uid) {
					final ResolveInfo r = new ResolveInfo() { @Override public String toString() { return "ResolveInfo{test}"; } };
					final ComponentInfo info = service_or_receiver ? (r.serviceInfo = new ServiceInfo()) : (r.activityInfo = new ActivityInfo());
					info.packageName = pkg;
					info.applicationInfo = new ApplicationInfo();
					info.applicationInfo.packageName = pkg;
					info.applicationInfo.uid = uid;
					return r;
				}
			};
		}

		void assertBaseCalled() { assertTrue(mBaseCalled); mBaseCalled = false; }
		void assertBaseNotCalled() { assertFalse(mBaseCalled); }

		Runnable expectFlags(final int flags) {
			return new Runnable() { @Override public void run() {
				assertEquals(flags | INTENT_FLAGS, mIntentFlags);
			}};
		}

		TestContext() { super((InstrumentationRegistry.getTargetContext())); }

		boolean mTestingBackgroundUid;
		boolean mTestingStoppedProvider;
		private int mIntentFlags;
		private boolean mBaseCalled;

		final Runnable EXPECT_BASE_CALLED = new Runnable() { @Override public void run() { assertBaseCalled(); } };
	}

	private interface Consumer<T> { void accept(T t); }
}
