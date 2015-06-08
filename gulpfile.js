'use strict';

var gulp = require('gulp');
var gutil = require('gulp-util');
var sass = require('gulp-sass');
var autoprefixer = require('gulp-autoprefixer');
var sourcemaps = require('gulp-sourcemaps');
var postcss = require('gulp-postcss');
var assets = require('postcss-assets');
var webpack = require('webpack');

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
};

gulp.task('sass', function () {
	gulp.src('src/sass/style.sass', {base: 'src/sass'})
		.pipe(sourcemaps.init())
		.pipe(sass().on('error', sass.logError))
		.pipe(autoprefixer({
			browsers: ['last 1 version'],
		}))
		.pipe(postcss([assets({
		})]))
		.pipe(sourcemaps.write())
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

gulp.task('watch', function () {
	gulp.watch('src/sass/*.sass', ['sass']);

	gulp.watch('src/audio/**/*.js', ['webpack']);
});

gulp.task('default', ['sass', 'webpack']);
