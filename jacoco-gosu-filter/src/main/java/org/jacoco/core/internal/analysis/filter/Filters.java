/*******************************************************************************
 * CUSTOM JACOCO FILTERS CLASS WITH GOSU NULL-SAFETY FILTER
 *
 * This is a modified version of JaCoCo's Filters class that includes
 * the GosuNullSafetyFilter to filter out Gosu's null-safe navigation bytecode.
 *
 * Original Copyright (c) 2009, 2025 Mountainminds GmbH & Co. KG and Contributors
 * Modifications: Added GosuNullSafetyFilter integration
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.jacoco.core.internal.analysis.filter;

import org.objectweb.asm.tree.MethodNode;

/**
 * Factory for all JaCoCo filters, including custom Gosu filter.
 *
 * <p>This class replaces JaCoCo's original Filters class and adds
 * GosuNullSafetyFilter to the filter chain.</p>
 */
public final class Filters {

	private static final String LOG_PREFIX = "[GosuFilters]";
	private static boolean logged = false;

	private Filters() {
		// no instances
	}

	/**
	 * Filter that does nothing.
	 */
	public static final IFilter NONE = new FilterSet();

	/**
	 * Creates a filter that combines all filters, including Gosu filter.
	 *
	 * @return filter that combines all filters
	 */
	public static IFilter all() {
		// Log once that our custom Filters class is being used
		if (!logged) {
			System.out.println(LOG_PREFIX + " ========================================");
			System.out.println(LOG_PREFIX + " CUSTOM FILTERS CLASS LOADED");
			System.out.println(LOG_PREFIX + " Including GosuNullSafetyFilter");
			System.out.println(LOG_PREFIX + " ========================================");
			logged = true;
		}

		final IFilter allCommonFilters = allCommonFilters();
		final IFilter allKotlinFilters = allKotlinFilters();
		final IFilter allNonKotlinFilters = allNonKotlinFilters();
		final IFilter gosuFilter = gosuFilters();  // ADD: Gosu filter

		return new IFilter() {
			public void filter(final MethodNode methodNode,
					final IFilterContext context, final IFilterOutput output) {
				allCommonFilters.filter(methodNode, context, output);
				if (isKotlinClass(context)) {
					allKotlinFilters.filter(methodNode, context, output);
				} else {
					allNonKotlinFilters.filter(methodNode, context, output);
				}
				// ADD: Apply Gosu filter to all non-Kotlin classes
				if (!isKotlinClass(context)) {
					gosuFilter.filter(methodNode, context, output);
				}
			}
		};
	}

	private static IFilter allCommonFilters() {
		return new FilterSet( //
				new EnumFilter(), //
				new BridgeFilter(), //
				new SynchronizedFilter(), //
				new TryWithResourcesJavac11Filter(), //
				new TryWithResourcesJavacFilter(), //
				new TryWithResourcesEcjFilter(), //
				new FinallyFilter(), //
				new PrivateEmptyNoArgConstructorFilter(), //
				new AssertFilter(), //
				new StringSwitchJavacFilter(), //
				new StringSwitchFilter(), //
				new EnumEmptyConstructorFilter(), //
				new RecordsFilter(), //
				new ExhaustiveSwitchFilter(), //
				new RecordPatternFilter(), //
				new AnnotationGeneratedFilter());
	}

	private static IFilter allNonKotlinFilters() {
		return new FilterSet( //
				new SyntheticFilter());
	}

	private static IFilter allKotlinFilters() {
		return new FilterSet( //
				new KotlinGeneratedFilter(), //
				new KotlinSyntheticAccessorsFilter(), //
				new KotlinEnumFilter(), //
				new KotlinJvmOverloadsFilter(), //
				new KotlinSafeCallOperatorFilter(), //
				new KotlinLateinitFilter(), //
				new KotlinWhenFilter(), //
				new KotlinWhenStringFilter(), //
				new KotlinUnsafeCastOperatorFilter(), //
				new KotlinNotNullOperatorFilter(), //
				new KotlinInlineClassFilter(), //
				new KotlinDefaultArgumentsFilter(), //
				new KotlinInlineFilter(), //
				new KotlinCoroutineFilter(), //
				new KotlinDefaultMethodsFilter(), //
				new KotlinComposeFilter());
	}

	/**
	 * Creates Gosu-specific filters.
	 *
	 * @return filter set for Gosu null-safety patterns
	 */
	private static IFilter gosuFilters() {
		try {
			// Try to load GosuNullSafetyFilter
			Class<?> gosuFilterClass = Class.forName("org.jacoco.gosu.GosuNullSafetyFilter");
			IFilter filter = (IFilter) gosuFilterClass.getDeclaredConstructor().newInstance();
			System.out.println(LOG_PREFIX + " ✓ GosuNullSafetyFilter loaded successfully");
			return new FilterSet(filter);
		} catch (ClassNotFoundException e) {
			System.err.println(LOG_PREFIX + " ⚠ GosuNullSafetyFilter not found, skipping Gosu filtering");
			System.err.println(LOG_PREFIX + "   Make sure gosu-filter-agent.jar is in classpath");
			return NONE;
		} catch (Exception e) {
			System.err.println(LOG_PREFIX + " ✗ Failed to load GosuNullSafetyFilter: " + e.getMessage());
			e.printStackTrace();
			return NONE;
		}
	}

	/**
	 * Checks whether the class corresponding to the given context has
	 * <code>kotlin/Metadata</code> annotation.
	 *
	 * @param context
	 *            context information
	 * @return <code>true</code> if the class corresponding to the given context
	 *         has <code>kotlin/Metadata</code> annotation
	 */
	public static boolean isKotlinClass(final IFilterContext context) {
		return context.getClassAnnotations()
				.contains(KotlinGeneratedFilter.KOTLIN_METADATA_DESC);
	}

}
