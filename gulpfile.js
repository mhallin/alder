'use strict';

var gulp = require('gulp');
var autoprefixer = require('gulp-autoprefixer');
var minifyCss = require('gulp-minify-css');
var postcss = require('gulp-postcss');
var rev = require('gulp-rev');
var sass = require('gulp-sass');
var gutil = require('gulp-util');
var assets = require('postcss-assets');
var webpack = require('webpack');
var argv = require('yargs').argv;

var production = !!(argv.production);

var webpackConfig = {
	entry: './src/audio/all.js',
	output: {
		path: 'resources/public/js_compiled',
		filename: 'audio_all.js',
	},
	module: {
		preLoaders: [
			{
				test: /\.js$/,
				exclude: /node_modules/,
				loader: 'jshint-loader',
			},
		],
	},
	jshint: {
		globalstrict: true,
	},
	plugins: [
		new webpack.optimize.OccurrenceOrderPlugin(),
	],
};

if (production) {
	webpackConfig.plugins.push(new webpack.optimize.UglifyJsPlugin());
}

gulp.task('sass', function () {
	return gulp.src('src/sass/style.sass', {base: 'src/sass'})
		.pipe(sass().on('error', sass.logError))
		.pipe(autoprefixer({
			browsers: ['last 1 version'],
		}))
		.pipe(postcss([assets({
		})]))
		.pipe(minifyCss())
		.pipe(gulp.dest('resources/public/css_compiled'));
});

gulp.task('webpack', function (callback) {
	webpack(webpackConfig).run(function (err, stats) {
		if (err) {
			throw new gutil.PluginError('webpack', err);
		}

		gutil.log('[webpack]', stats.toString({colors: true}));

		callback();
	});
});

gulp.task('compress', function (callback) {
	return gulp.src(['resources/public/**/*'])
		.pipe(rev())
		.pipe(gulp.dest('resources/compressed'))
		.pipe(rev.manifest())
		.pipe(gulp.dest('resources'));
});

gulp.task('watch', function () {
	gulp.watch('src/sass/*.sass', ['sass']);

	gulp.watch('src/audio/**/*.js', ['webpack']);
});

gulp.task('default', ['sass', 'webpack']);
gulp.task('prepare-cdn', ['default', 'compress']);
